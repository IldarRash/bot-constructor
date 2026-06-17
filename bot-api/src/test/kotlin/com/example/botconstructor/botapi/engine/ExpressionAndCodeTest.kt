package com.example.botconstructor.botapi.engine

import com.example.botconstructor.botapi.model.dto.FlowEdge
import com.example.botconstructor.botapi.model.dto.FlowNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for the GraalVM-backed expression/JS foundation: the opt-in `{{= ... }}` inline JS form and
 * the `code` node. The sandbox must fail soft — a throwing expression yields `""` and a throwing Code
 * program passes items through / routes `error` — so the engine keeps walking either way.
 */
class ExpressionAndCodeTest {

    private val fallback = "Sorry, I did not understand"

    private val noHttp = HttpCaller { _, _, _, _ ->
        throw AssertionError("no HTTP call expected in this test")
    }

    private fun run(
            text: String,
            nodes: List<FlowNode>,
            edges: List<FlowEdge>,
            initialVars: Map<String, Any?> = emptyMap(),
    ): MatchResult = WorkflowEngine.run(text, nodes, edges, fallback, noHttp, initialVars).block()!!

    // --- inline JS interpolation -----------------------------------------------------------------

    @Test
    fun `inline JS expression is evaluated when the placeholder opts in with =`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("msg", "sendMessage", data = mapOf("text" to "{{= message.toUpperCase() }}")),
        )
        val edges = listOf(FlowEdge("e1", "trigger", "msg"))
        val result = run("hello", nodes, edges)

        assertThat(result.reply).isEqualTo("HELLO")
    }

    @Test
    fun `inline JS can read the whole namespace via the json alias`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("msg", "sendMessage", data = mapOf("text" to "Hi {{= \$json.user.name }}!")),
        )
        val edges = listOf(FlowEdge("e1", "trigger", "msg"))
        val result = run("x", nodes, edges, initialVars = mapOf("user" to mapOf("name" to "Ada")))

        assertThat(result.reply).isEqualTo("Hi Ada!")
    }

    @Test
    fun `a malformed or throwing JS expression yields empty string and the engine still runs`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("msg", "sendMessage", data = mapOf("text" to "[{{= nope.boom() }}]")),
        )
        val edges = listOf(FlowEdge("e1", "trigger", "msg"))
        val result = run("hi", nodes, edges)

        assertThat(result.reply).isEqualTo("[]")
    }

    @Test
    fun `a non-= placeholder still uses pure dotted-path resolution`() {
        // No code execution: '=' is required to opt in, so this is plain path traversal.
        assertThat(WorkflowEngine.interpolate("{{user.name}}", mapOf("user" to mapOf("name" to "Bob"))))
                .isEqualTo("Bob")
    }

    // --- code node -------------------------------------------------------------------------------

    @Test
    fun `code node transforms items by mapping json`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("code", "code", data = mapOf(
                        "code" to "\$items.map(it => ({ shout: (it.message || '').toUpperCase() }))",
                )),
                FlowNode("msg", "sendMessage", data = mapOf("text" to "{{shout}}")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "code"),
                FlowEdge("e2", "code", "msg"),
        )
        val result = run("hello", nodes, edges)

        assertThat(result.reply).isEqualTo("HELLO")
    }

    @Test
    fun `code node routes the error handle when the program throws`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("code", "code", data = mapOf("code" to "throw new Error('boom')")),
                FlowNode("ok", "sendMessage", data = mapOf("text" to "ok branch")),
                FlowNode("err", "sendMessage", data = mapOf("text" to "error branch")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "code"),
                FlowEdge("e2", "code", "ok"),
                FlowEdge("e3", "code", "err", sourceHandle = "error"),
        )
        val result = run("hi", nodes, edges)

        assertThat(result.reply).isEqualTo("error branch")
    }

    @Test
    fun `code node error falls through the default handle when there is no error edge`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("code", "code", data = mapOf("code" to "throw new Error('boom')")),
                FlowNode("after", "sendMessage", data = mapOf("text" to "continued")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "code"),
                FlowEdge("e2", "code", "after"),
        )
        val result = run("hi", nodes, edges)

        // On error the original items pass through; with no error edge they follow the default output.
        assertThat(result.reply).isEqualTo("continued")
    }
}

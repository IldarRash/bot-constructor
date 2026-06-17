package com.example.botconstructor.botapi.engine

import com.example.botconstructor.botapi.model.dto.FlowEdge
import com.example.botconstructor.botapi.model.dto.FlowNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * End-to-end tests for the Phase C library nodes, exercised through `WorkflowEngine.run` so they also
 * verify registration and that each node's output bucket keys match the edges' `sourceHandle`s (the
 * silent-failure mode: a handle/bucket mismatch carries no items downstream).
 */
class LibraryNodesTest {

    private val fallback = "Sorry, I did not understand"
    private val noHttp = HttpCaller { _, _, _, _ -> throw AssertionError("no HTTP expected") }

    private fun run(
            text: String,
            nodes: List<FlowNode>,
            edges: List<FlowEdge>,
            initialVars: Map<String, Any?> = emptyMap(),
    ): MatchResult = WorkflowEngine.run(text, nodes, edges, fallback, noHttp, initialVars).block()!!

    @Test
    fun `if routes to the true handle when both clauses hold with combine and`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("if", "if", data = mapOf(
                        "left" to "{{message}}", "op" to "contains", "right" to "yes",
                        "combine" to "and",
                        "left2" to "{{message}}", "op2" to "contains", "right2" to "please")),
                FlowNode("yes", "sendMessage", data = mapOf("text" to "ok")),
                FlowNode("no", "sendMessage", data = mapOf("text" to "nope")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "if"),
                FlowEdge("e2", "if", "yes", sourceHandle = "true"),
                FlowEdge("e3", "if", "no", sourceHandle = "false"),
        )
        assertThat(run("yes please", nodes, edges).reply).isEqualTo("ok")
        assertThat(run("yes only", nodes, edges).reply).isEqualTo("nope")
    }

    @Test
    fun `switch routes a matching case to its handle and otherwise to the default output`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("sw", "switch", data = mapOf("value" to "{{message}}", "case0" to "hi", "case1" to "bye")),
                FlowNode("m0", "sendMessage", data = mapOf("text" to "zero")),
                FlowNode("m1", "sendMessage", data = mapOf("text" to "one")),
                FlowNode("md", "sendMessage", data = mapOf("text" to "default")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "sw"),
                FlowEdge("e2", "sw", "m0", sourceHandle = "0"),
                FlowEdge("e3", "sw", "m1", sourceHandle = "1"),
                FlowEdge("e4", "sw", "md"), // default output = null sourceHandle
        )
        assertThat(run("hi", nodes, edges).reply).isEqualTo("zero")
        assertThat(run("bye", nodes, edges).reply).isEqualTo("one")
        assertThat(run("other", nodes, edges).reply).isEqualTo("default")
    }

    @Test
    fun `filter drops items that fail the predicate`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("f", "filter", data = mapOf("left" to "{{message}}", "op" to "eq", "right" to "keep")),
                FlowNode("m", "sendMessage", data = mapOf("text" to "passed")),
        )
        val edges = listOf(FlowEdge("e1", "trigger", "f"), FlowEdge("e2", "f", "m"))
        assertThat(run("keep", nodes, edges).reply).isEqualTo("passed")
        // Dropped: no item reaches the sendMessage, so the walk falls back.
        assertThat(run("drop", nodes, edges).reply).isEqualTo(fallback)
    }

    @Test
    fun `splitOut multiplies items so a downstream node runs once per element`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("split", "splitOut", data = mapOf("field" to "items")),
                FlowNode("m", "sendMessage", data = mapOf("text" to "{{itemsItem}}")),
        )
        val edges = listOf(FlowEdge("e1", "trigger", "split"), FlowEdge("e2", "split", "m"))
        val result = run("go", nodes, edges, initialVars = mapOf("items" to listOf("a", "b", "c")))
        assertThat(result.reply).isEqualTo("a\nb\nc")
    }

    @Test
    fun `set writes several fields readable by a later node`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("set", "set", data = mapOf("assignments" to "a=1\nb=Hi {{message}}")),
                FlowNode("m", "sendMessage", data = mapOf("text" to "{{a}}-{{b}}")),
        )
        val edges = listOf(FlowEdge("e1", "trigger", "set"), FlowEdge("e2", "set", "m"))
        assertThat(run("Sam", nodes, edges).reply).isEqualTo("1-Hi Sam")
    }

    @Test
    fun `wait passes its items through unchanged`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("w", "wait", data = mapOf("seconds" to "0")),
                FlowNode("m", "sendMessage", data = mapOf("text" to "done")),
        )
        val edges = listOf(FlowEdge("e1", "trigger", "w"), FlowEdge("e2", "w", "m"))
        assertThat(run("hi", nodes, edges).reply).isEqualTo("done")
    }

    @Test
    fun `noop passes items straight through`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("n", "noop"),
                FlowNode("m", "sendMessage", data = mapOf("text" to "through")),
        )
        val edges = listOf(FlowEdge("e1", "trigger", "n"), FlowEdge("e2", "n", "m"))
        assertThat(run("hi", nodes, edges).reply).isEqualTo("through")
    }
}

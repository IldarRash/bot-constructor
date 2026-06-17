package com.example.botconstructor.botapi.engine

import com.example.botconstructor.botapi.model.dto.FlowEdge
import com.example.botconstructor.botapi.model.dto.FlowNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for the item-based scheduler's routing contract (Phase A): multi-output fan-out, fan-in with
 * run-once semantics, and that branch buckets route items independently. These lock in the behavior
 * the later merge/loop nodes (Phase C) will deliberately extend.
 */
class ItemFlowTest {

    private val fallback = "Sorry, I did not understand"

    private val noHttp = HttpCaller { _, _, _, _ ->
        throw AssertionError("no HTTP call expected in this test")
    }

    private fun run(nodes: List<FlowNode>, edges: List<FlowEdge>): MatchResult =
            WorkflowEngine.run("hi", nodes, edges, fallback, noHttp).block()!!

    @Test
    fun `a node fans out to multiple default-output targets, both run in edge order`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("m1", "sendMessage", data = mapOf("text" to "one")),
                FlowNode("m2", "sendMessage", data = mapOf("text" to "two")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "m1"),
                FlowEdge("e2", "trigger", "m2"),
        )
        val result = run(nodes, edges)

        assertThat(result.reply).isEqualTo("one\ntwo")
    }

    @Test
    fun `a diamond fan-in runs the join node once over the gathered items`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("a", "sendMessage", data = mapOf("text" to "A")),
                FlowNode("b", "sendMessage", data = mapOf("text" to "B")),
                FlowNode("c", "sendMessage", data = mapOf("text" to "C")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "a"),
                FlowEdge("e2", "trigger", "b"),
                FlowEdge("e3", "a", "c"),
                FlowEdge("e4", "b", "c"),
        )
        val result = run(nodes, edges)

        // c is reached from both a and b: it executes a single time but over the two gathered items,
        // so sendMessage emits "C" once per item — true item-model behavior, not two node runs.
        assertThat(result.reply).isEqualTo("A\nB\nC\nC")
        assertThat(result.trace.count { it.nodeId == "c" }).isEqualTo(1)
    }

    @Test
    fun `condition routes only the matching branch and the other never executes`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("cond", "condition", data = mapOf("left" to "{{message}}", "op" to "eq", "right" to "hi")),
                FlowNode("yes", "sendMessage", data = mapOf("text" to "matched")),
                FlowNode("no", "sendMessage", data = mapOf("text" to "missed")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "cond"),
                FlowEdge("e2", "cond", "yes", sourceHandle = "true"),
                FlowEdge("e3", "cond", "no", sourceHandle = "false"),
        )
        val result = run(nodes, edges)

        assertThat(result.reply).isEqualTo("matched")
        assertThat(result.trace.map { it.nodeId }).doesNotContain("no")
    }
}

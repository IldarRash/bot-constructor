package com.example.botconstructor.botapi.engine

import com.example.botconstructor.botapi.model.dto.FlowEdge
import com.example.botconstructor.botapi.model.dto.FlowNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Engine-level tests for the additive Merge (gather) and Loop (re-entry) scheduler markers, exercised
 * through [WorkflowEngine.run]. They lock in the two behaviors a normal run-once node cannot provide:
 * a gather that waits for ALL forward inputs before running once, and a feedback loop that re-runs its
 * body per batch and terminates. The existing `ItemFlowTest`/`WorkflowEngineTest` suites assert the
 * unchanged normal-node path stays byte-identical.
 */
class MergeLoopTest {

    private val fallback = "Sorry, I did not understand"
    private val noHttp = HttpCaller { _, _, _, _ -> throw AssertionError("no HTTP expected") }

    private fun run(
            text: String,
            nodes: List<FlowNode>,
            edges: List<FlowEdge>,
            initialVars: Map<String, Any?> = emptyMap(),
    ): MatchResult = WorkflowEngine.run(text, nodes, edges, fallback, noHttp, initialVars).block()!!

    // --- Merge (gather) ----------------------------------------------------------------------

    @Test
    fun `merge gathers across unequal path lengths and runs once over both items`() {
        // trigger --short--> merge ; trigger -> set -> set -> merge (forward in-degree 2).
        // A downstream sendMessage fires once per gathered item => two replies.
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("s1", "setVariable", data = mapOf("name" to "a", "value" to "x")),
                FlowNode("s2", "setVariable", data = mapOf("name" to "b", "value" to "y")),
                FlowNode("merge", "merge"),
                FlowNode("out", "sendMessage", data = mapOf("text" to "item")),
        )
        val edges = listOf(
                FlowEdge("eShort", "trigger", "merge"),
                FlowEdge("e1", "trigger", "s1"),
                FlowEdge("e2", "s1", "s2"),
                FlowEdge("e3", "s2", "merge"),
                FlowEdge("e4", "merge", "out"),
        )
        val result = run("go", nodes, edges)

        // Merge ran exactly once, over BOTH gathered items (short-path item + long-path item).
        assertThat(result.trace.count { it.nodeId == "merge" }).isEqualTo(1)
        assertThat(result.trace.single { it.nodeId == "merge" }.detail).isEqualTo("merge 2 items")
        assertThat(result.reply).isEqualTo("item\nitem")
    }

    @Test
    fun `a plain join node would pop after the short edge with only one item`() {
        // Same shape, but the join is a normal sendMessage (no gather marker): run-once means it
        // executes when the FIRST (short) edge delivers — over a single item. This contrast is exactly
        // the gather value the merge node adds above.
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("s1", "setVariable", data = mapOf("name" to "a", "value" to "x")),
                FlowNode("s2", "setVariable", data = mapOf("name" to "b", "value" to "y")),
                FlowNode("join", "sendMessage", data = mapOf("text" to "item")),
        )
        val edges = listOf(
                FlowEdge("eShort", "trigger", "join"),
                FlowEdge("e1", "trigger", "s1"),
                FlowEdge("e2", "s1", "s2"),
                FlowEdge("e3", "s2", "join"),
        )
        val result = run("go", nodes, edges)

        // Normal node ran once, but only over the single short-path item (one reply) — no gather.
        assertThat(result.trace.count { it.nodeId == "join" }).isEqualTo(1)
        assertThat(result.reply).isEqualTo("item")
    }

    @Test
    fun `merge with a dead branch finalizes without hanging over the items it received`() {
        // One live condition branch feeds merge; the other merge input is on the false branch that is
        // never taken. forwardInDegree[merge] = 2 but only one settles => finalization must rescue it.
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("cond", "condition", data = mapOf("left" to "{{message}}", "op" to "eq", "right" to "go")),
                FlowNode("live", "setVariable", data = mapOf("name" to "a", "value" to "x")),
                FlowNode("dead", "setVariable", data = mapOf("name" to "b", "value" to "y")),
                FlowNode("merge", "merge"),
                FlowNode("out", "sendMessage", data = mapOf("text" to "ok")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "cond"),
                FlowEdge("e2", "cond", "live", sourceHandle = "true"),
                FlowEdge("e3", "cond", "dead", sourceHandle = "false"),
                FlowEdge("e4", "live", "merge"),
                FlowEdge("e5", "dead", "merge"),
                FlowEdge("e6", "merge", "out"),
        )
        val result = run("go", nodes, edges)

        // No hang; merge ran once over the single item it actually received (the live branch).
        assertThat(result.trace.count { it.nodeId == "merge" }).isEqualTo(1)
        assertThat(result.trace.single { it.nodeId == "merge" }.detail).isEqualTo("merge 1 items")
        assertThat(result.reply).isEqualTo("ok")
        // The dead branch never executed.
        assertThat(result.trace.map { it.nodeId }).doesNotContain("dead")
    }

    // --- Loop (re-entry) ---------------------------------------------------------------------

    @Test
    fun `loop iterates a batch at a time then fires done once and terminates`() {
        // trigger -> splitOut(3) -> loop(1) --loop--> body --back--> loop ; loop --done--> done
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("split", "splitOut", data = mapOf("field" to "items")),
                FlowNode("loop", "loop", data = mapOf("batchSize" to "1")),
                FlowNode("body", "sendMessage", data = mapOf("text" to "x={{itemsItem}}")),
                FlowNode("done", "sendMessage", data = mapOf("text" to "done")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "split"),
                FlowEdge("e2", "split", "loop"),
                FlowEdge("e3", "loop", "body", sourceHandle = "loop"),
                FlowEdge("e4", "body", "loop"), // feedback (back-edge)
                FlowEdge("e5", "loop", "done", sourceHandle = "done"),
        )
        val result = run("go", nodes, edges, initialVars = mapOf("items" to listOf("a", "b", "c")))

        // Body ran once per batch (3 times), over each element in turn.
        assertThat(result.trace.count { it.nodeId == "body" }).isEqualTo(3)
        // done fired exactly once (the node executed a single time after all batches).
        assertThat(result.trace.count { it.nodeId == "done" }).isEqualTo(1)
        // The body emitted one reply per batched item (x=a, x=b, x=c); `done` then ran once over the
        // full stashed payload (all 3 items) => one "done" reply per item. The walk terminated.
        assertThat(result.reply).isEqualTo("x=a\nx=b\nx=c\ndone\ndone\ndone")
    }

    @Test
    fun `loop batchSize greater than one batches correctly`() {
        // 5 items, batchSize 2 => batches [a,b] [c,d] [e] => 3 body runs.
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("split", "splitOut", data = mapOf("field" to "items")),
                FlowNode("loop", "loop", data = mapOf("batchSize" to "2")),
                FlowNode("body", "sendMessage", data = mapOf("text" to "batch")),
                FlowNode("done", "sendMessage", data = mapOf("text" to "done")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "split"),
                FlowEdge("e2", "split", "loop"),
                FlowEdge("e3", "loop", "body", sourceHandle = "loop"),
                FlowEdge("e4", "body", "loop"),
                FlowEdge("e5", "loop", "done", sourceHandle = "done"),
        )
        val result = run("go", nodes, edges, initialVars = mapOf("items" to listOf("a", "b", "c", "d", "e")))

        // 3 batches => 3 body runs; first batch emits two replies (one per item), etc.
        val bodyRuns = result.nodeTraces.filter { it.nodeId == "body" }
        assertThat(bodyRuns).hasSize(3)
        assertThat(bodyRuns.map { it.inputItems.size }).containsExactly(2, 2, 1)
        assertThat(result.trace.count { it.nodeId == "done" }).isEqualTo(1)
    }

    @Test
    fun `loop batchSize of zero or below clamps to one`() {
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("split", "splitOut", data = mapOf("field" to "items")),
                FlowNode("loop", "loop", data = mapOf("batchSize" to "0")),
                FlowNode("body", "sendMessage", data = mapOf("text" to "x")),
                FlowNode("done", "sendMessage", data = mapOf("text" to "done")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "split"),
                FlowEdge("e2", "split", "loop"),
                FlowEdge("e3", "loop", "body", sourceHandle = "loop"),
                FlowEdge("e4", "body", "loop"),
                FlowEdge("e5", "loop", "done", sourceHandle = "done"),
        )
        val result = run("go", nodes, edges, initialVars = mapOf("items" to listOf("a", "b")))

        // Clamp to 1 => one body run per item (2 runs), then done.
        assertThat(result.trace.count { it.nodeId == "body" }).isEqualTo(2)
        assertThat(result.trace.count { it.nodeId == "done" }).isEqualTo(1)
    }

    @Test
    fun `merge inside a loop body gathers the full forward in-degree on every batch`() {
        // Regression: a Merge nested in a Loop body must reset its gather accumulator on each batch.
        // loop --loop--> body ; body --short--> merge ; body -> s1 -> s2 --> merge (long) ;
        // merge --back--> loop. forwardInDegree(merge) = 2, so every batch must gather BOTH inputs.
        // Before the fix `arrived[merge]` survived across batches, so batches 2+ partial-gathered.
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("split", "splitOut", data = mapOf("field" to "items")),
                FlowNode("loop", "loop", data = mapOf("batchSize" to "1")),
                FlowNode("body", "setVariable", data = mapOf("name" to "b", "value" to "x")),
                FlowNode("s1", "setVariable", data = mapOf("name" to "a", "value" to "x")),
                FlowNode("s2", "setVariable", data = mapOf("name" to "c", "value" to "y")),
                FlowNode("merge", "merge"),
                FlowNode("done", "sendMessage", data = mapOf("text" to "done")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "split"),
                FlowEdge("e2", "split", "loop"),
                FlowEdge("e3", "loop", "body", sourceHandle = "loop"),
                FlowEdge("eShort", "body", "merge"), // short forward edge into merge
                FlowEdge("eL1", "body", "s1"), // long path: body -> s1 -> s2 -> merge
                FlowEdge("eL2", "s1", "s2"),
                FlowEdge("eL3", "s2", "merge"),
                FlowEdge("eBack", "merge", "loop"), // feedback (back-edge)
                FlowEdge("e5", "loop", "done", sourceHandle = "done"),
        )
        val result = run("go", nodes, edges, initialVars = mapOf("items" to listOf("a", "b", "c")))

        // 3 batches => merge ran 3 times, and EACH run gathered the full forward in-degree (2 items:
        // the short-edge item + the long-path item). Pre-fix the middle batch gathered only 1.
        val mergeRuns = result.trace.filter { it.nodeId == "merge" }
        assertThat(mergeRuns).hasSize(3)
        assertThat(mergeRuns.map { it.detail }).containsExactly(
                "merge 2 items", "merge 2 items", "merge 2 items")
    }

    @Test
    fun `loop over zero items emits done immediately and runs the body never`() {
        val directNodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("filter", "filter", data = mapOf("left" to "1", "op" to "eq", "right" to "2")),
                FlowNode("loop", "loop", data = mapOf("batchSize" to "1")),
                FlowNode("body", "sendMessage", data = mapOf("text" to "x")),
                FlowNode("done", "sendMessage", data = mapOf("text" to "done")),
        )
        val directEdges = listOf(
                FlowEdge("e1", "trigger", "filter"),
                FlowEdge("e2", "filter", "loop"),
                FlowEdge("e3", "loop", "body", sourceHandle = "loop"),
                FlowEdge("e4", "body", "loop"),
                FlowEdge("e5", "loop", "done", sourceHandle = "done"),
        )
        // filter drops the only item => loop receives zero items. firstEntry emits an empty `loop`
        // and an empty `done` payload, then clears its state — so neither the body nor the downstream
        // `done` sendMessage (which has no item to fire over) runs, and the walk terminates cleanly.
        val result = WorkflowEngine.run("go", directNodes, directEdges, fallback, noHttp).block()!!

        assertThat(result.trace.count { it.nodeId == "body" }).isEqualTo(0)
        assertThat(result.trace.count { it.nodeId == "done" }).isEqualTo(0)
        // The loop node still executed once (its zero-items first entry), then the walk ended.
        assertThat(result.trace.count { it.nodeId == "loop" }).isEqualTo(1)
        assertThat(result.reply).isEqualTo(fallback)
    }
}

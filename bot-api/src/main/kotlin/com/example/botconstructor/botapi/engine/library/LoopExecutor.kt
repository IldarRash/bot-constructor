package com.example.botconstructor.botapi.engine.library

import com.example.botconstructor.botapi.engine.ExecutionItem
import com.example.botconstructor.botapi.engine.NodeInput
import com.example.botconstructor.botapi.engine.NodeOutput
import com.example.botconstructor.botapi.engine.RunContext
import com.example.botconstructor.botapi.engine.SyncExecutor
import com.example.botconstructor.botapi.engine.traceStep
import com.example.botconstructor.botapi.model.dto.FlowNode

/**
 * `loop` node — Split-in-Batches. It implements [LoopExecutor], so the scheduler exempts it from
 * run-once: each delivery (the first forward delivery, then every feedback back-edge delivery) re-runs
 * it. Two outputs: `loop` (the current batch) and `done` (the full input items, after all batches).
 *
 * State machine, keyed in [RunContext.loopState] by `node.id`:
 *  - **First entry** (no state): stash the full input items as the `done` payload and a queue of
 *    `inputItems.chunked(batchSize)`. With zero input items, emit an empty `loop` AND the `done`
 *    payload immediately, clearing state. Otherwise emit the first batch on `loop`.
 *  - **Re-entry** (state exists; reached via the feedback edge — its fed-back items are ignored, the
 *    loop tracks its own batches): if batches remain, emit the next on `loop`; else emit the stashed
 *    full items on `done` and clear state.
 *
 * [MAX_BATCHES_PER_LOOP] caps total iterations of a single loop node (in addition to the engine's
 * `MAX_STEPS` backstop) so a misconfigured feedback can never spin. `batchSize` defaults to 1 and
 * clamps to `>= 1`. Records one trace step per run (`loop batch k/m` or `loop done`).
 */
object LoopExecutor : SyncExecutor, com.example.botconstructor.botapi.engine.LoopExecutor {

    /** Absolute per-loop-node iteration cap, layered under the engine's `MAX_STEPS`. */
    private const val MAX_BATCHES_PER_LOOP = 1000

    /** Mutable per-node loop progress stashed in [RunContext.loopState]. */
    private class State(
            val doneItems: List<ExecutionItem>,
            val batches: List<List<ExecutionItem>>,
    ) {
        var index: Int = 0
    }

    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): NodeOutput {
        val state = ctx.loopState[node.id] as? State
        return if (state == null) firstEntry(node, input, ctx) else reEntry(node, input, ctx, state)
    }

    private fun firstEntry(node: FlowNode, input: NodeInput, ctx: RunContext): NodeOutput {
        val batchSize = (node.data["batchSize"] as? String)?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val items = input.items
        if (items.isEmpty()) {
            // Nothing to iterate: emit an empty `loop` and the (empty) `done` payload at once.
            val output = NodeOutput(mapOf("loop" to emptyList(), "done" to emptyList()))
            traceStep(ctx, node, "done", "loop done", input, output)
            return output
        }
        val batches = items.chunked(batchSize)
        val state = State(doneItems = items, batches = batches).apply { index = 1 }
        ctx.loopState[node.id] = state
        val output = NodeOutput.handle("loop", batches[0])
        traceStep(ctx, node, "loop", "loop batch 1/${batches.size}", input, output)
        return output
    }

    private fun reEntry(node: FlowNode, input: NodeInput, ctx: RunContext, state: State): NodeOutput {
        // Re-entry ignores the fed-back items: the loop drives itself off its own batch queue.
        return if (state.index < state.batches.size && state.index < MAX_BATCHES_PER_LOOP) {
            val batch = state.batches[state.index]
            val k = state.index + 1
            state.index = k
            val output = NodeOutput.handle("loop", batch)
            traceStep(ctx, node, "loop", "loop batch $k/${state.batches.size}", input, output)
            output
        } else {
            ctx.loopState.remove(node.id)
            val output = NodeOutput.handle("done", state.doneItems)
            traceStep(ctx, node, "done", "loop done", input, output)
            output
        }
    }
}

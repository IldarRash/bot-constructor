package com.example.botconstructor.botapi.engine.library

import com.example.botconstructor.botapi.engine.GatherExecutor
import com.example.botconstructor.botapi.engine.NodeInput
import com.example.botconstructor.botapi.engine.NodeOutput
import com.example.botconstructor.botapi.engine.RunContext
import com.example.botconstructor.botapi.engine.SyncExecutor
import com.example.botconstructor.botapi.engine.traceStep
import com.example.botconstructor.botapi.model.dto.FlowNode

/**
 * `merge` node — the item-model fan-in join. It implements [GatherExecutor], so the scheduler holds it
 * back until every forward input has settled (or finalization rescues a partly-fed merge whose other
 * input sits on a never-taken branch), then runs it exactly once over the FULL accumulated inbox. A
 * plain (non-gather) join would instead pop after its first input with only those items; gathering is
 * the whole point of this node.
 *
 * One logical input (several edges may target it) and one default output. `mode` is currently only
 * `append`: it concatenates all gathered items in arrival order and emits them on the default (`null`)
 * output. Pure and non-throwing; records one trace step (`merge N items`).
 */
object MergeExecutor : SyncExecutor, GatherExecutor {
    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): NodeOutput {
        // "append" is the only mode for now: emit the gathered items verbatim, in arrival order.
        val output = NodeOutput.default(input.items)
        traceStep(ctx, node, null, "merge ${input.items.size} items", input, output)
        return output
    }
}

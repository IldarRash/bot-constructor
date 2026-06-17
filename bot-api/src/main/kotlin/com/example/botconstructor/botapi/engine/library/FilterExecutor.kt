package com.example.botconstructor.botapi.engine.library

import com.example.botconstructor.botapi.engine.EngineFns
import com.example.botconstructor.botapi.engine.NodeInput
import com.example.botconstructor.botapi.engine.NodeOutput
import com.example.botconstructor.botapi.engine.RunContext
import com.example.botconstructor.botapi.engine.SyncExecutor
import com.example.botconstructor.botapi.engine.traceStep
import com.example.botconstructor.botapi.model.dto.FlowNode

/**
 * `filter` node. Keeps only the input items for which `left op right` holds, dropping the rest
 * entirely — unlike [com.example.botconstructor.botapi.engine.ConditionExecutor], which routes both
 * sides to separate handles, a filter discards non-matching items so they never flow downstream.
 *
 * `left`/`right` are interpolated per item (so `{{message}}`-style expressions resolve against the
 * current item's vars), then compared with the same [EngineFns.evaluate] operator vocabulary
 * (`eq`/`neq`/`contains`/`gt`/`lt`). The kept items are emitted on the single default (`null`) output.
 * Pure and side-effect-free: it never throws and records exactly one [com.example.botconstructor.botapi.engine.NodeTrace].
 */
object FilterExecutor : SyncExecutor {
    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): NodeOutput {
        val op = (node.data["op"] as? String)?.trim() ?: ""
        val rawLeft = node.data["left"] as? String ?: ""
        val rawRight = node.data["right"] as? String ?: ""

        val kept = input.items.filter { item ->
            val vars = ctx.itemVars(item)
            val left = EngineFns.interpolate(rawLeft, vars)
            val right = EngineFns.interpolate(rawRight, vars)
            EngineFns.evaluate(left, op, right)
        }

        val output = NodeOutput.default(kept)
        traceStep(ctx, node, null, "filter kept ${kept.size}/${input.items.size}", input, output)
        return output
    }
}

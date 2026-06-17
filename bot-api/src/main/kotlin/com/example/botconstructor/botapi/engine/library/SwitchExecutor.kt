package com.example.botconstructor.botapi.engine.library

import com.example.botconstructor.botapi.engine.EngineFns
import com.example.botconstructor.botapi.engine.NodeInput
import com.example.botconstructor.botapi.engine.NodeOutput
import com.example.botconstructor.botapi.engine.RunContext
import com.example.botconstructor.botapi.engine.SyncExecutor
import com.example.botconstructor.botapi.engine.traceStep
import com.example.botconstructor.botapi.model.dto.FlowNode

/**
 * `switch` value router. Interpolates `value` and compares it (string equality) against the
 * interpolated `case0`/`case1` operands, routing the items to the first matching output handle:
 *  - equals `case0` → output `"0"`,
 *  - else equals `case1` → output `"1"`,
 *  - else → the default (`null` sourceHandle) output.
 *
 * Unlike `condition` this is an n-way fan-out by *value* rather than a boolean predicate, so it
 * mirrors a `switch`/`case` statement: the resolved value picks exactly one lane.
 *
 * Data shape: `{ value, case0, case1 }`, all `{{expr}}`-interpolated against the current item.
 * `value` resolves against the *first* input item (single-lineage chat flows carry one item), and
 * every input item is routed together onto the chosen bucket — only that bucket is populated; the
 * other two are emitted empty so their edges simply receive nothing. Never consumes the turn,
 * never throws: an unresolvable expression interpolates to `""`, which falls through to `default`.
 */
object SwitchExecutor : SyncExecutor {
    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): NodeOutput {
        // Resolve operands against the first item; an empty input degrades to the chat-compat vars.
        val item = input.items.firstOrNull()
        val vars = if (item != null) ctx.itemVars(item) else ctx.varsView.toMap()
        val v = EngineFns.interpolate(node.data["value"] as? String ?: "", vars)
        val case0 = EngineFns.interpolate(node.data["case0"] as? String ?: "", vars)
        val case1 = EngineFns.interpolate(node.data["case1"] as? String ?: "", vars)

        // The default lane is the `null` sourceHandle so it matches the engine's default-bucket
        // contract (and the frontend's id-less default Handle); `case0`/`case1` keep their ids.
        val handle: String? = when (v) {
            case0 -> "0"
            case1 -> "1"
            else -> null
        }

        // Only the chosen lane carries the items; the engine routes the empty lanes to nowhere.
        val output =
                NodeOutput(
                        mapOf(
                                "0" to (if (handle == "0") input.items else emptyList()),
                                "1" to (if (handle == "1") input.items else emptyList()),
                                null to (if (handle == null) input.items else emptyList()),
                        )
                )
        traceStep(ctx, node, handle, "switch \"$v\" -> ${handle ?: "default"}", input, output)
        return output
    }
}

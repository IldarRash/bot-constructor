package com.example.botconstructor.botapi.engine.library

import com.example.botconstructor.botapi.engine.EngineFns
import com.example.botconstructor.botapi.engine.ExecutionItem
import com.example.botconstructor.botapi.engine.NodeInput
import com.example.botconstructor.botapi.engine.NodeOutput
import com.example.botconstructor.botapi.engine.RunContext
import com.example.botconstructor.botapi.engine.SyncExecutor
import com.example.botconstructor.botapi.engine.branchOutput
import com.example.botconstructor.botapi.engine.traceStep
import com.example.botconstructor.botapi.model.dto.FlowNode

/**
 * `if` branch — a generalized boolean gate over the [ConditionExecutor][com.example.botconstructor.botapi.engine.ConditionExecutor]
 * semantics with an optional second clause. For each input item it interpolates and evaluates
 * `clause1 = left op right`; when the optional `left2`/`op2`/`right2` triple is fully populated it
 * also evaluates `clause2 = left2 op2 right2` and folds the two with `combine` (`and` => `&&`,
 * `or` => `||`, default `and`). Items for which the combined predicate holds route to the `true`
 * bucket, the rest to `false`.
 *
 * Both buckets are always emitted ([NodeOutput] with `true`/`false` keys) so each branch's outgoing
 * edges route independently — dead branches simply receive an empty list. Pure and non-throwing:
 * every operand defaults to an empty string, unknown operators evaluate to `false`
 * ([EngineFns.evaluate]), and exactly one [NodeTrace][com.example.botconstructor.botapi.engine.NodeTrace]
 * is recorded per run.
 */
object IfExecutor : SyncExecutor {
    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): NodeOutput {
        val left = node.data["left"] as? String ?: ""
        val op = (node.data["op"] as? String)?.trim() ?: ""
        val right = node.data["right"] as? String ?: ""

        val left2 = node.data["left2"] as? String ?: ""
        val op2 = (node.data["op2"] as? String)?.trim() ?: ""
        val right2 = node.data["right2"] as? String ?: ""
        // The second clause is opt-in: only honored when all three of its operands are present.
        val hasClause2 = left2.isNotBlank() && op2.isNotBlank() && right2.isNotBlank()
        val combine = (node.data["combine"] as? String)?.trim()?.lowercase() ?: "and"

        val trueItems = mutableListOf<ExecutionItem>()
        val falseItems = mutableListOf<ExecutionItem>()
        input.items.forEach { item ->
            val vars = ctx.itemVars(item)
            val clause1 = EngineFns.evaluate(
                    EngineFns.interpolate(left, vars), op, EngineFns.interpolate(right, vars))
            val outcome = if (hasClause2) {
                val clause2 = EngineFns.evaluate(
                        EngineFns.interpolate(left2, vars), op2, EngineFns.interpolate(right2, vars))
                if (combine == "or") clause1 || clause2 else clause1 && clause2
            } else {
                clause1
            }
            (if (outcome) trueItems else falseItems).add(item)
        }

        // Shared branch contract: both buckets emitted, primary handle = whichever got items.
        val (output, handle) = branchOutput(trueItems, falseItems)
        val detail = "IF (${if (hasClause2) combine else "single"}) -> $handle"
        traceStep(ctx, node, handle, detail, input, output)
        return output
    }
}

package com.example.botconstructor.botapi.engine.library

import com.example.botconstructor.botapi.engine.EngineFns
import com.example.botconstructor.botapi.engine.ExecutionItem
import com.example.botconstructor.botapi.engine.NodeInput
import com.example.botconstructor.botapi.engine.NodeOutput
import com.example.botconstructor.botapi.engine.RunContext
import com.example.botconstructor.botapi.engine.SyncExecutor
import com.example.botconstructor.botapi.engine.traceStep
import com.example.botconstructor.botapi.model.dto.FlowNode

/**
 * `splitOut` action — the item-model "loop over an array". For each input item it resolves the array
 * at `data.field` (a dotted path walked through nested [Map]s, layered over [RunContext.itemVars] so
 * both the current item's `json` and the shared `varsView` are visible) and fans the item out into
 * one output item per array element:
 *  - **element is a Map** → `ExecutionItem(json = item.json + element)`, so the element's own keys
 *    become first-class fields on the new item (the n8n "split out into items" behavior).
 *  - **element is a scalar** → `ExecutionItem(json = item.json + (<lastSegment>Item -> element))`, so
 *    the value stays addressable by a flat, derived top-level key.
 *
 * When `field` resolves to something that is **not** a list (missing or scalar) the original item is
 * passed through unchanged — splitting nothing must never drop data. Everything leaves on the default
 * (`null`) output; multiplying the item list here is all that is needed for downstream nodes to run
 * once per element, so this is a true fan-out with no scheduler change.
 *
 * Records exactly one [com.example.botconstructor.botapi.engine.NodeTrace] per run (`split <field> ->
 * N items`, where N is the total emitted count). [run] never throws: a blank `field` or any
 * unresolvable path simply yields pass-through items.
 */
object SplitOutExecutor : SyncExecutor {
    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): NodeOutput {
        val field = (node.data["field"] as? String)?.trim().orEmpty()

        val items: List<ExecutionItem> = if (field.isEmpty()) {
            // No field configured: there is nothing to split on, so forward items untouched.
            input.items
        } else {
            input.items.flatMap { item ->
                when (val value = EngineFns.resolvePath(field, ctx.itemVars(item))) {
                    is List<*> -> value.map { element -> explode(item, field, element) }
                    // Missing or scalar: pass the original item through rather than dropping it.
                    else -> listOf(item)
                }
            }
        }

        val output = NodeOutput.default(items)
        traceStep(ctx, node, null, "split $field -> ${items.size} items", input, output)
        return output
    }

    /**
     * Builds the fanned-out item for one array [element]: a Map element merges over `item.json`
     * (its keys become item fields); a scalar element is stored under `<lastSegment>Item`. The key is
     * derived from the last path segment (not the whole dotted `field`) so the value stays addressable
     * as a flat top-level key by `{{expr}}` even when `field` is a dotted path (e.g. `order.items`
     * → `itemsItem`, not the unreachable `order.itemsItem`).
     */
    private fun explode(item: ExecutionItem, field: String, element: Any?): ExecutionItem =
            if (element is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                item.withJson(element as Map<String, Any?>)
            } else {
                item.withJson(mapOf(field.substringAfterLast('.') + "Item" to element))
            }
}

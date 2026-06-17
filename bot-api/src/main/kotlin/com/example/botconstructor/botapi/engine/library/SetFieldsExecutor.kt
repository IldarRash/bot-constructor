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
 * `set` mutation — generalizes `setVariable` to write several fields at once.
 *
 * `data.assignments` is a newline-separated block of `name=value` lines: each non-blank line is split
 * on its **first** `=`, the name is trimmed, and the value is interpolated against the current item's
 * variables (`{{expr}}` supported, exactly like `setVariable`). Every resolved pair is written into
 * the item's `json` under `name` and mirrored into the chat-compat [RunContext.varsView] so the
 * single-lineage chat flows keep seeing a shared `vars` map.
 *
 * Parsing is deliberately forgiving: blank lines and lines without an `=` are ignored, and a line
 * whose name trims to empty is skipped (its value is never evaluated). When no assignment applies the
 * node still forwards its input items unchanged on the default output.
 */
object SetFieldsExecutor : SyncExecutor {
    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): NodeOutput {
        // Parse once into (name, rawValue) pairs; interpolation happens per item so each item resolves
        // its own `json` layer. Names are pre-trimmed and blanks dropped here so the per-item loop is
        // a straight fold over valid assignments.
        val assignments = parseAssignments(node.data["assignments"] as? String)

        val output: NodeOutput
        if (assignments.isNotEmpty()) {
            val items = input.items.map { item ->
                val writes = LinkedHashMap<String, Any?>(assignments.size)
                assignments.forEach { (name, rawValue) ->
                    // Resolve against the item's current view, layering earlier writes so later lines
                    // can reference fields set by earlier ones within the same node.
                    val value = EngineFns.interpolate(rawValue, ctx.itemVars(item) + writes)
                    writes[name] = value
                    ctx.varsView[name] = value
                }
                item.withJson(writes)
            }
            output = NodeOutput.default(items)
        } else {
            output = NodeOutput.default(input.items)
        }

        val detail = "set ${assignments.size} field(s)"
        traceStep(ctx, node, null, detail, input, output)
        return output
    }

    /**
     * Splits the raw `assignments` block into ordered `(name, rawValue)` pairs. Blank lines, lines
     * without an `=`, and lines whose name trims to empty are dropped; the value keeps its original
     * (untrimmed) text so leading/trailing spaces an author intends are preserved before interpolation.
     */
    private fun parseAssignments(raw: String?): List<Pair<String, String>> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.lineSequence()
                .mapNotNull { line ->
                    if (line.isBlank()) return@mapNotNull null
                    val eq = line.indexOf('=')
                    if (eq < 0) return@mapNotNull null
                    val name = line.substring(0, eq).trim()
                    if (name.isEmpty()) return@mapNotNull null
                    name to line.substring(eq + 1)
                }
                .toList()
    }
}

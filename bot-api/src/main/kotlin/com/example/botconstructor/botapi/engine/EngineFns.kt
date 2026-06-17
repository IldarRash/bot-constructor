package com.example.botconstructor.botapi.engine

import com.example.botconstructor.botapi.model.dto.FlowEdge

/**
 * Pure, Spring-free helper functions shared by the node executors and the engine: expression
 * interpolation, condition evaluation, keyword matching, HTTP routing/trace, and the related
 * constants. Extracted from the original `WorkflowEngine` so executors can reuse the exact same,
 * already-tested semantics. No IO, no reflection, no mutable state.
 */
object EngineFns {

    /** Per-walk cap on executed `httpRequest` nodes; bounds outbound-request amplification. */
    const val MAX_HTTP_REQUESTS: Int = 5

    /**
     * Request headers a bot owner may NOT set: routing / hop-by-hop headers whose client control
     * enables request smuggling or connection abuse. Matched case-insensitively and dropped.
     */
    val DENIED_HEADERS: Set<String> = setOf("host", "content-length", "connection", "transfer-encoding")

    /** Splits text into whole-word tokens for keyword matching. */
    val tokenSplit: Regex = Regex("\\W+")

    /** Matches a single `{{ ... }}` placeholder; group 1 is the (untrimmed) inner path. */
    private val expressionPattern: Regex = Regex("\\{\\{(.*?)}}")

    /**
     * Resolves `{{ ... }}` expressions in [text] against [vars]. Two opt-in forms:
     *  - **dotted path** (the default): the trimmed inner text is split on `.` and traversed through
     *    nested [Map]s; the resolved value's string form is substituted, an unresolvable path → `""`.
     *    Pure map/path traversal — no code execution, no reflection. This is the backward-compatible
     *    behavior every existing template relies on.
     *  - **sandboxed JS** (`{{= expr }}`): when the trimmed inner text starts with `=`, the remainder
     *    is evaluated as JavaScript via [ExpressionEvaluator.evalToString] against [vars] (which also
     *    exposes `$vars`/`$json`/`$now`). JS only runs when the author opts in with `=`, so the common
     *    path stays allocation-cheap and the engine's deterministic guarantees are preserved.
     */
    fun interpolate(text: String, vars: Map<String, Any?>): String =
            expressionPattern.replace(text) { match ->
                val inner = match.groupValues[1].trim()
                if (inner.startsWith("=")) {
                    ExpressionEvaluator.evalToString(inner.substring(1).trim(), vars)
                } else {
                    resolvePath(inner, vars)?.toString() ?: ""
                }
            }

    /**
     * Traverses [path] (already trimmed) through [vars], descending into nested [Map]s on each `.`
     * segment. Returns the resolved value, or null when any segment is missing or a non-map value is
     * encountered mid-path. `internal` so library nodes (e.g. `splitOut`) reuse the exact same
     * dotted-path semantics as interpolation rather than re-implementing the walk.
     */
    internal fun resolvePath(path: String, vars: Map<String, Any?>): Any? {
        if (path.isEmpty()) return null
        var current: Any? = vars
        for (segment in path.split('.')) {
            val map = current as? Map<*, *> ?: return null
            current = map[segment] ?: return null
        }
        return current
    }

    /**
     * Finds the first keyword in [keyWords] that hits [tokens] (whole word) or appears as a
     * substring of [normalized] (both case-insensitive), or null when none match.
     */
    fun keywordHit(keyWords: List<String>, normalized: String, tokens: Set<String>): String? =
            keyWords.firstOrNull { keyWord ->
                val needle = keyWord.lowercase().trim()
                needle.isNotEmpty() && (needle in tokens || normalized.contains(needle))
            }

    /**
     * Evaluates `left op right` over already-interpolated string operands:
     *  - `eq`/`neq`   string (in)equality.
     *  - `contains`   case-insensitive substring test.
     *  - `gt`/`lt`    numeric compare when both parse as Double, else lexicographic.
     * Unknown operators evaluate to false.
     */
    fun evaluate(left: String, op: String, right: String): Boolean = when (op) {
        "eq" -> left == right
        "neq" -> left != right
        "contains" -> left.contains(right, ignoreCase = true)
        "gt" -> compareOperands(left, right) > 0
        "lt" -> compareOperands(left, right) < 0
        else -> false
    }

    /** Numeric comparison when both operands parse as [Double], otherwise lexicographic. */
    private fun compareOperands(left: String, right: String): Int {
        val l = left.toDoubleOrNull()
        val r = right.toDoubleOrNull()
        return if (l != null && r != null) l.compareTo(r) else left.compareTo(right)
    }

    /**
     * Picks the handle a failed `httpRequest` follows: `"error"` when at least one edge leaves the
     * node on that handle, otherwise the default (`null`) output.
     */
    fun errorOrDefaultHandle(nodeId: String, edges: List<FlowEdge>): String? =
            if (edges.any { it.source == nodeId && it.sourceHandle == "error" }) "error" else null

    /**
     * Summarizes an HTTP call for the trace: `GET <url> -> 200` on success, `-> blocked` when the
     * call never produced a response (status 0), else `-> error` for a non-2xx response.
     */
    fun httpDetail(verb: String, url: String, res: HttpCallResult): String {
        val outcome = when {
            res.ok -> res.statusCode.toString()
            res.statusCode == 0 -> "blocked"
            else -> "error"
        }
        return "$verb $url -> $outcome"
    }
}

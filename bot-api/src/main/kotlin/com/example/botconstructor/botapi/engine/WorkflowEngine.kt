package com.example.botconstructor.botapi.engine

import com.example.botconstructor.botapi.model.dto.FlowEdge
import com.example.botconstructor.botapi.model.dto.FlowNode
import com.example.botconstructor.botapi.model.dto.TraceStep
import reactor.core.publisher.Mono

/**
 * The keyword node that fired during a walk.
 *
 * @property label The fired keyword node's label (or its id when no label is set).
 */
data class MatchedSummary(
        val label: String,
)

/**
 * Outcome of walking the graph over a user message.
 *
 * @property reply The answer text to return (joined replies, or the fallback when empty).
 * @property matched The keyword node that fired, or null when the fallback answer was used.
 * @property trace The per-node execution trace, in execution order.
 * @property vars The final variable snapshot after the walk.
 */
data class MatchResult(
        val reply: String,
        val matched: MatchedSummary?,
        val trace: List<TraceStep>,
        val vars: Map<String, Any?>,
)

/**
 * Mutable per-walk execution state. Seeds `message`; [vars] is the place phases (setVariable,
 * httpRequest, expressions) read and write. [trace] records one [TraceStep] per executed node, in
 * order, for the execution-trace debugger.
 *
 * @property userText The raw incoming user message.
 * @param initialVars Extra variables seeded into [vars] before the walk (e.g. webhook payload
 *   `vars`). `message` is always seeded last from [userText], so it cannot be shadowed.
 */
class ExecutionContext(val userText: String, initialVars: Map<String, Any?> = emptyMap()) {
    val vars: MutableMap<String, Any?> = initialVars.toMutableMap().apply { put("message", userText) }
    val replies: MutableList<String> = mutableListOf()
    val trace: MutableList<TraceStep> = mutableListOf()
}

/**
 * Spring-free, reactive graph-walking engine.
 *
 * Starting from the single `trigger` node, the engine follows outgoing edges in edge order,
 * executing each reachable node by [FlowNode.type]:
 *  - `trigger`     no-op; follows all default-handle outgoing edges.
 *  - `keyword`     matches its `keyWords` against the user text (same rule as the legacy engine);
 *                  on match records the label and follows `match` edges, else follows `nomatch`. The
 *                  first `match` consumes the turn (sibling keyword guards are abandoned).
 *  - `sendMessage` appends its `text` (routed through [interpolate]) to the reply buffer.
 *  - `setVariable` sets `vars[name] = interpolate(value)`; follows the default output.
 *  - `condition`   branch point: interpolates `left`/`right`, evaluates `op`, and follows the
 *                  `true`/`false` handle. Does NOT consume the turn (only `keyword` does).
 *  - `httpRequest` calls [HttpCaller]; on success stores the parsed body into `vars[saveAs]` and
 *                  follows the default output, on error follows the `error` handle if any edge uses
 *                  it (else the default). This is the only async node; the walk awaits it before
 *                  continuing.
 *
 * Pure (non-IO) nodes resolve synchronously inside the reactive chain — only `httpRequest` introduces
 * an actual `Mono` boundary. A visited-set / max-steps cap of [MAX_STEPS] guards against cycles. The
 * first keyword label that fires is reported as the match; the joined replies form the answer, or
 * [fallbackAnswer] if none.
 */
object WorkflowEngine {

    private const val MAX_STEPS = 100

    /**
     * Per-walk cap on executed `httpRequest` nodes. Bounds outbound-request amplification: once a
     * walk has performed this many HTTP calls, further `httpRequest` nodes are NOT dialed and are
     * treated as a failed call (routed to `error`/default), so a graph cannot fan a single inbound
     * message into unbounded outbound traffic.
     */
    private const val MAX_HTTP_REQUESTS = 5

    /**
     * Request headers a bot owner may NOT set: routing / hop-by-hop headers whose client control
     * enables request smuggling or connection abuse. Matched case-insensitively and dropped before
     * the call. Other headers pass through.
     */
    private val DENIED_HEADERS = setOf("host", "content-length", "connection", "transfer-encoding")

    private val tokenSplit = Regex("\\W+")

    /** Matches a single `{{ ... }}` placeholder; group 1 is the (untrimmed) inner path. */
    private val expressionPattern = Regex("\\{\\{(.*?)}}")

    /**
     * Mutable cursor threaded through the reactive walk. Holding the frontier and turn flags in one
     * object keeps the recursive [step] signature small while preserving the original BFS semantics.
     */
    private class WalkState(triggerId: String) {
        /** Node ids still to execute, in breadth-first / edge order. */
        val frontier: ArrayDeque<String> = ArrayDeque()
        val visited: MutableSet<String> = mutableSetOf(triggerId)
        var steps: Int = 0

        /**
         * Once a keyword fires its `match` handle it consumes the turn: sibling keyword guards are
         * abandoned so exactly one branch replies, preserving the legacy single-answer rule.
         */
        var turnConsumed: Boolean = false
        var firstMatch: MatchedSummary? = null

        /** Count of `httpRequest` nodes that have actually performed (or attempted) a call. */
        var httpRequests: Int = 0
    }

    /**
     * Walks the graph reactively over [text] and resolves to the [MatchResult]. Synchronous nodes
     * complete inline; the lone async node (`httpRequest`) suspends the chain on [httpCaller] before
     * the walk resumes.
     */
    fun run(
            text: String,
            nodes: List<FlowNode>,
            edges: List<FlowEdge>,
            fallbackAnswer: String,
            httpCaller: HttpCaller,
            initialVars: Map<String, Any?> = emptyMap(),
    ): Mono<MatchResult> {
        val ctx = ExecutionContext(text, initialVars)
        val nodesById = nodes.associateBy { it.id }
        val trigger = nodes.firstOrNull { it.type == "trigger" }
                ?: return Mono.just(result(ctx, fallbackAnswer, null))

        val normalized = text.lowercase()
        val tokens = normalized.split(tokenSplit).filter { it.isNotBlank() }.toSet()

        val state = WalkState(trigger.id)
        // The trigger is the entry point; record it as the first trace step (it follows its default
        // output) since the walk seeds its children rather than executing it in [step].
        ctx.trace.add(TraceStep(nodeId = trigger.id, type = trigger.type, handle = null, detail = "start"))
        state.frontier.addAll(outgoing(trigger.id, null, edges))

        return step(state, ctx, nodesById, edges, normalized, tokens, httpCaller)
                .map { result(ctx, fallbackAnswer, state.firstMatch) }
    }

    private fun result(ctx: ExecutionContext, fallbackAnswer: String, matched: MatchedSummary?): MatchResult {
        val reply = if (ctx.replies.isNotEmpty()) ctx.replies.joinToString("\n") else fallbackAnswer
        return MatchResult(
                reply = reply,
                matched = matched,
                trace = ctx.trace.toList(),
                vars = ctx.vars.toMap(),
        )
    }

    /**
     * Processes one frontier node then recurses for the rest. Pure nodes recurse synchronously via a
     * tail call; `httpRequest` returns the recursion wrapped in a [Mono.flatMap] so the walk only
     * continues after the HTTP call resolves. Returns when the frontier drains or the step cap hits.
     */
    private fun step(
            state: WalkState,
            ctx: ExecutionContext,
            nodesById: Map<String, FlowNode>,
            edges: List<FlowEdge>,
            normalized: String,
            tokens: Set<String>,
            httpCaller: HttpCaller,
    ): Mono<Unit> {
        // Drain non-executing frontier entries (already-visited / unknown / consumed keyword guards)
        // in a loop so we only build a Mono per node that actually runs.
        while (true) {
            if (state.frontier.isEmpty() || state.steps >= MAX_STEPS) return DONE
            state.steps++
            val nodeId = state.frontier.removeFirst()
            if (!state.visited.add(nodeId)) continue
            val node = nodesById[nodeId] ?: continue
            // A prior keyword already won the turn; ignore remaining sibling guards entirely.
            if (node.type == "keyword" && state.turnConsumed) continue

            if (node.type == "httpRequest") {
                // The only async node: await the call, store the result, queue children, then resume.
                return executeHttpRequest(node, ctx, state, edges, httpCaller).flatMap {
                    step(state, ctx, nodesById, edges, normalized, tokens, httpCaller)
                }
            }

            val nextHandle = executeSync(node, ctx, normalized, tokens, state)
            state.frontier.addAll(outgoing(nodeId, nextHandle, edges))
            // Continue draining the frontier synchronously (no IO boundary for this node).
        }
    }

    /** A pre-completed `Mono<Unit>` used as the synchronous terminator of the walk. */
    private val DONE: Mono<Unit> = Mono.just(Unit)

    /**
     * Executes a synchronous (non-IO) node, mutating [ctx]/[state], records its trace step, and
     * returns the output handle to follow (`null` = default output).
     */
    private fun executeSync(
            node: FlowNode,
            ctx: ExecutionContext,
            normalized: String,
            tokens: Set<String>,
            state: WalkState,
    ): String? {
        var detail: String? = null
        val handle: String? = when (node.type) {
            "keyword" -> {
                val keyWords = (node.data["keyWords"] as? List<*>)
                        ?.filterIsInstance<String>()
                        ?: emptyList()
                val hit = keyWords.firstOrNull { keyWord ->
                    val needle = keyWord.lowercase().trim()
                    needle.isNotEmpty() && (needle in tokens || normalized.contains(needle))
                }
                if (hit != null) {
                    state.turnConsumed = true
                    val label = (node.data["label"] as? String) ?: node.id
                    state.firstMatch = MatchedSummary(label)
                    detail = "matched: $hit"
                    "match"
                } else {
                    detail = "no match"
                    "nomatch"
                }
            }

            "sendMessage" -> {
                val msg = node.data["text"] as? String
                val text = if (msg != null) interpolate(msg, ctx.vars) else ""
                if (msg != null) ctx.replies.add(text)
                detail = text
                null
            }

            "setVariable" -> {
                val name = (node.data["name"] as? String)?.trim()
                val rawValue = node.data["value"] as? String ?: ""
                // A blank name skips the write but still follows the default output.
                if (!name.isNullOrEmpty()) {
                    val value = interpolate(rawValue, ctx.vars)
                    ctx.vars[name] = value
                    detail = "$name = $value"
                } else {
                    detail = "skipped (blank name)"
                }
                null
            }

            "condition" -> {
                val left = interpolate(node.data["left"] as? String ?: "", ctx.vars)
                val right = interpolate(node.data["right"] as? String ?: "", ctx.vars)
                val op = (node.data["op"] as? String)?.trim() ?: ""
                // condition is an automatic branch point; it never consumes the turn.
                val outcome = if (evaluate(left, op, right)) "true" else "false"
                detail = "\"$left\" $op \"$right\" -> $outcome"
                outcome
            }

            else -> null // trigger or unknown: follow default output
        }
        ctx.trace.add(TraceStep(nodeId = node.id, type = node.type, handle = handle, detail = detail))
        return handle
    }

    /**
     * Executes an `httpRequest` node: interpolates `url`, `body`, and header values, calls
     * [httpCaller], stores the parsed response into `vars[saveAs]`, and queues the children of the
     * handle to follow. On a successful (2xx) call the default output is followed; otherwise the
     * `error` handle is followed when any edge uses it, else the default output. The call never
     * crashes the walk: [HttpCaller] reports failures as non-ok results.
     *
     * `data`: `{method, url, headers?: Map, body?: String, saveAs: String}`.
     *
     * The per-walk [MAX_HTTP_REQUESTS] cap is enforced first: once exceeded, the call is NOT made and
     * the node is routed as a failed call. Routing/hop-by-hop headers are stripped before the call.
     */
    private fun executeHttpRequest(
            node: FlowNode,
            ctx: ExecutionContext,
            state: WalkState,
            edges: List<FlowEdge>,
            httpCaller: HttpCaller,
    ): Mono<Unit> {
        val saveAs = (node.data["saveAs"] as? String)?.trim()
        val method = (node.data["method"] as? String)?.trim()?.ifEmpty { null } ?: "GET"
        val url = interpolate(node.data["url"] as? String ?: "", ctx.vars)

        // Amplification guard: stop dialing once the per-walk cap is hit; treat as a failed call.
        state.httpRequests++
        if (state.httpRequests > MAX_HTTP_REQUESTS) {
            applyHttpResult(
                    HttpCallResult(statusCode = 0, body = null, ok = false),
                    node, ctx, state, edges, saveAs,
                    detail = "${method.uppercase()} $url -> cap exceeded",
            )
            return DONE
        }

        val body = (node.data["body"] as? String)?.let { interpolate(it, ctx.vars) }
        val headers = (node.data["headers"] as? Map<*, *>)
                ?.entries
                ?.mapNotNull { (k, v) ->
                    val key = k as? String ?: return@mapNotNull null
                    // Deny client control of routing / hop-by-hop headers (case-insensitive).
                    if (key.trim().lowercase() in DENIED_HEADERS) return@mapNotNull null
                    key to interpolate(v?.toString() ?: "", ctx.vars)
                }
                ?.toMap()
                ?: emptyMap()

        val verb = method.uppercase()
        return httpCaller.call(verb, url, headers, body).map { res ->
            applyHttpResult(res, node, ctx, state, edges, saveAs, detail = httpDetail(verb, url, res))
        }
    }

    /**
     * Summarizes an HTTP call for the trace: `GET <url> -> 200` on success, `-> blocked` when the
     * call never produced a response (status 0), else `-> error` for a non-2xx response.
     */
    private fun httpDetail(verb: String, url: String, res: HttpCallResult): String {
        val outcome = when {
            res.ok -> res.statusCode.toString()
            res.statusCode == 0 -> "blocked"
            else -> "error"
        }
        return "$verb $url -> $outcome"
    }

    /**
     * Stores the response into `vars[saveAs]` (if any), records the trace step, and queues the
     * children of the chosen handle.
     */
    private fun applyHttpResult(
            res: HttpCallResult,
            node: FlowNode,
            ctx: ExecutionContext,
            state: WalkState,
            edges: List<FlowEdge>,
            saveAs: String?,
            detail: String,
    ) {
        if (!saveAs.isNullOrEmpty()) {
            // Store the parsed body so later nodes reach nested fields via `{{saveAs.field}}`.
            ctx.vars[saveAs] = res.body
        }
        val handle = if (res.ok) null else errorOrDefaultHandle(node.id, edges)
        ctx.trace.add(TraceStep(nodeId = node.id, type = node.type, handle = handle, detail = detail))
        state.frontier.addAll(outgoing(node.id, handle, edges))
    }

    /**
     * Picks the handle a failed `httpRequest` follows: `"error"` when at least one edge leaves the
     * node on that handle, otherwise the default (`null`) output.
     */
    private fun errorOrDefaultHandle(nodeId: String, edges: List<FlowEdge>): String? =
            if (edges.any { it.source == nodeId && it.sourceHandle == "error" }) "error" else null

    /** Target node ids of edges leaving [nodeId] on [handle], in edge declaration order. */
    private fun outgoing(nodeId: String, handle: String?, edges: List<FlowEdge>): List<String> =
            edges.filter { it.source == nodeId && it.sourceHandle == handle }.map { it.target }

    /**
     * Resolves `{{ ... }}` expressions in [text] against [vars]. A placeholder's inner path is
     * trimmed, split on `.`, and traversed through nested [Map]s; the resolved value's string form
     * is substituted. An unresolvable path resolves to an empty string. Text outside `{{ }}` is
     * left unchanged. This is a pure function: no code execution, no reflection — only map/path
     * traversal.
     */
    internal fun interpolate(text: String, vars: Map<String, Any?>): String =
            expressionPattern.replace(text) { match ->
                resolvePath(match.groupValues[1].trim(), vars)?.toString() ?: ""
            }

    /**
     * Traverses [path] (already trimmed) through [vars], descending into nested [Map]s on each `.`
     * segment. Returns the resolved value, or null when any segment is missing or a non-map value
     * is encountered mid-path.
     */
    private fun resolvePath(path: String, vars: Map<String, Any?>): Any? {
        if (path.isEmpty()) return null
        var current: Any? = vars
        for (segment in path.split('.')) {
            val map = current as? Map<*, *> ?: return null
            current = map[segment] ?: return null
        }
        return current
    }

    /**
     * Evaluates `left op right` over already-interpolated string operands:
     *  - `eq`/`neq`   string (in)equality.
     *  - `contains`   case-insensitive substring test.
     *  - `gt`/`lt`    numeric compare when both parse as Double, else lexicographic string compare.
     * Unknown operators evaluate to false.
     */
    private fun evaluate(left: String, op: String, right: String): Boolean = when (op) {
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
}

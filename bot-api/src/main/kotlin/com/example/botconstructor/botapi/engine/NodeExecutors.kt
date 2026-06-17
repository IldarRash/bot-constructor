package com.example.botconstructor.botapi.engine

import com.example.botconstructor.botapi.model.dto.FlowNode
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers

/**
 * The built-in node executors (Phase 0–2 node types) migrated to the item model. Each preserves the
 * exact semantics of the original `WorkflowEngine.executeSync`/`executeHttpRequest` so the existing
 * engine tests stay green, while now operating on item lists so branching/merge/loop nodes can build
 * on the same contract.
 */

/** Unknown / no-op nodes: pass input items through on the default output, record an empty detail. */
object PassThroughExecutor : SyncExecutor {
    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): NodeOutput {
        val output = NodeOutput.default(input.items)
        traceStep(ctx, node, null, null, input, output)
        return output
    }
}

/**
 * `keyword` guard. Matches its `keyWords` against the user text (token or substring,
 * case-insensitive). On a hit it consumes the turn, records the label as the walk's match, and emits
 * input items on the `match` handle; otherwise it emits them on `nomatch`.
 */
object KeywordExecutor : SyncExecutor {
    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): NodeOutput {
        val keyWords = (node.data["keyWords"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val hit = EngineFns.keywordHit(keyWords, ctx.normalized, ctx.tokens)
        return if (hit != null) {
            ctx.turnConsumed = true
            val label = (node.data["label"] as? String) ?: node.id
            ctx.firstMatch = MatchedSummary(label)
            val output = NodeOutput.handle("match", input.items)
            traceStep(ctx, node, "match", "matched: $hit", input, output)
            output
        } else {
            val output = NodeOutput.handle("nomatch", input.items)
            traceStep(ctx, node, "nomatch", "no match", input, output)
            output
        }
    }
}

/** `sendMessage` action. Appends its interpolated `text` to the reply buffer, once per input item. */
object SendMessageExecutor : SyncExecutor {
    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): NodeOutput {
        val raw = node.data["text"] as? String
        var detail = ""
        input.items.forEach { item ->
            if (raw != null) {
                val text = EngineFns.interpolate(raw, ctx.itemVars(item))
                ctx.replies.add(text)
                detail = text
            }
        }
        val output = NodeOutput.default(input.items)
        traceStep(ctx, node, null, detail, input, output)
        return output
    }
}

/**
 * `setVariable` mutation. Writes `interpolate(value)` into each item's `json` under `name` (and
 * mirrors it into the chat-compat `varsView`). A blank `name` skips the write but still follows the
 * default output.
 */
object SetVariableExecutor : SyncExecutor {
    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): NodeOutput {
        val name = (node.data["name"] as? String)?.trim()
        val rawValue = node.data["value"] as? String ?: ""
        val output: NodeOutput
        val detail: String
        if (!name.isNullOrEmpty()) {
            var lastValue = ""
            val items = input.items.map { item ->
                val value = EngineFns.interpolate(rawValue, ctx.itemVars(item))
                ctx.varsView[name] = value
                lastValue = value
                item.withJson(mapOf(name to value))
            }
            output = NodeOutput.default(items)
            detail = "$name = $lastValue"
        } else {
            output = NodeOutput.default(input.items)
            detail = "skipped (blank name)"
        }
        traceStep(ctx, node, null, detail, input, output)
        return output
    }
}

/**
 * `condition` branch. Interpolates `left`/`right` and evaluates `op` per item, partitioning items
 * into the `true`/`false` output buckets. Never consumes the turn.
 */
object ConditionExecutor : SyncExecutor {
    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): NodeOutput {
        val op = (node.data["op"] as? String)?.trim() ?: ""
        val trueItems = mutableListOf<ExecutionItem>()
        val falseItems = mutableListOf<ExecutionItem>()
        var detail = ""
        input.items.forEach { item ->
            val left = EngineFns.interpolate(node.data["left"] as? String ?: "", ctx.itemVars(item))
            val right = EngineFns.interpolate(node.data["right"] as? String ?: "", ctx.itemVars(item))
            val outcome = EngineFns.evaluate(left, op, right)
            (if (outcome) trueItems else falseItems).add(item)
            detail = "\"$left\" $op \"$right\" -> ${if (outcome) "true" else "false"}"
        }
        // Both buckets are emitted so each branch's edges route independently; the trace's primary
        // handle is whichever side received items (single-item flows hit exactly one).
        val (output, handle) = branchOutput(trueItems, falseItems)
        traceStep(ctx, node, handle, detail, input, output)
        return output
    }
}

/**
 * `httpRequest` action — the only async node. Interpolates `url`/`body`/header values against the
 * first input item, makes the SSRF-hardened call, stores the parsed body into the item's `json`
 * under `saveAs` (and `varsView`), and follows the default output on 2xx or the `error` handle on
 * failure. The per-walk [EngineFns.MAX_HTTP_REQUESTS] cap is enforced first; routing/hop-by-hop
 * headers are stripped before the call. When `data.credentialId` references an `httpHeaderAuth` /
 * `httpBearerAuth` credential, its resolved auth header is merged on top of the configured headers.
 */
object HttpRequestExecutor : AsyncExecutor {
    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): Mono<NodeOutput> {
        val saveAs = (node.data["saveAs"] as? String)?.trim()
        val method = (node.data["method"] as? String)?.trim()?.ifEmpty { null } ?: "GET"
        val verb = method.uppercase()
        val item = input.items.firstOrNull() ?: ExecutionItem(ctx.varsView.toMap())
        val vars = ctx.itemVars(item)
        val url = EngineFns.interpolate(node.data["url"] as? String ?: "", vars)

        // Amplification guard: stop dialing once the per-walk cap is hit; treat as a failed call.
        ctx.httpRequests++
        if (ctx.httpRequests > EngineFns.MAX_HTTP_REQUESTS) {
            return Mono.just(applyResult(
                    node, ctx, input, item, saveAs,
                    HttpCallResult(statusCode = 0, body = null, ok = false),
                    "$verb $url -> cap exceeded",
            ))
        }

        val body = (node.data["body"] as? String)?.let { EngineFns.interpolate(it, vars) }
        val headers = (node.data["headers"] as? Map<*, *>)
                ?.entries
                ?.mapNotNull { (k, v) ->
                    val key = k as? String ?: return@mapNotNull null
                    if (key.trim().lowercase() in EngineFns.DENIED_HEADERS) return@mapNotNull null
                    key to EngineFns.interpolate(v?.toString() ?: "", vars)
                }
                ?.toMap()
                ?: emptyMap()

        // Optional credential injection: a referenced httpHeaderAuth/httpBearerAuth adds one auth
        // header on top of the configured headers, before the SSRF call. The secret value flows only
        // into the header map below — never into the item, trace, or any log. An unset or unresolvable
        // credentialId leaves the request as configured (the credential is additive auth here).
        return resolveAuthHeader(node, ctx).flatMap { authHeader ->
            ctx.httpCaller.call(verb, url, headers + authHeader, body).map { res ->
                applyResult(node, ctx, input, item, saveAs, res, EngineFns.httpDetail(verb, url, res))
            }
        }
    }

    /**
     * Resolves `data.credentialId` into the extra auth header(s) to merge into the request, or an
     * empty map when no credential is referenced or it cannot be resolved:
     *  - `httpHeaderAuth` → `{ headerName: headerValue }`;
     *  - `httpBearerAuth` → `{ Authorization: Bearer <token> }`.
     * Always emits exactly one map (default empty) so the request proceeds regardless.
     */
    private fun resolveAuthHeader(node: FlowNode, ctx: RunContext): Mono<Map<String, String>> {
        val credentialId = (node.data["credentialId"] as? String)?.trim().orEmpty()
        if (credentialId.isEmpty()) return Mono.just(emptyMap())
        return ctx.resolveCredential(credentialId)
                .map { secret -> authHeaderFor(secret) }
                .defaultIfEmpty(emptyMap())
                .onErrorReturn(emptyMap())
    }

    /** Maps a resolved [CredentialSecret] to the auth header(s) to inject (empty for any other type). */
    private fun authHeaderFor(secret: CredentialSecret): Map<String, String> = when (secret.type) {
        "httpHeaderAuth" -> {
            val name = secret.data["headerName"].orEmpty()
            if (name.isBlank()) emptyMap() else mapOf(name to secret.data["headerValue"].orEmpty())
        }
        "httpBearerAuth" -> mapOf("Authorization" to "Bearer ${secret.data["token"].orEmpty()}")
        else -> emptyMap()
    }

    /**
     * Stores the response into the item's `json`/`varsView` (when `saveAs` is set), records the
     * trace step, and returns the items on the chosen handle (default on 2xx, else `error`/default).
     */
    private fun applyResult(
            node: FlowNode,
            ctx: RunContext,
            input: NodeInput,
            item: ExecutionItem,
            saveAs: String?,
            res: HttpCallResult,
            detail: String,
    ): NodeOutput {
        val outItem = if (!saveAs.isNullOrEmpty()) {
            ctx.varsView[saveAs] = res.body
            item.withJson(mapOf(saveAs to res.body))
        } else {
            item
        }
        val handle = if (res.ok) null else EngineFns.errorOrDefaultHandle(node.id, ctx.edges)
        val output = NodeOutput.handle(handle, listOf(outItem))
        traceStep(ctx, node, handle, detail, input, output)
        return output
    }
}

/**
 * `code` action — runs the author's JavaScript (`data.code`) over the input items inside the
 * [ExpressionEvaluator] sandbox. The input items' `json` maps are passed in as `$items`; the body's
 * result (an array of objects, or a mutated/returned `$items`) becomes the new item list, emitted on
 * the default output. The GraalVM call is offloaded to [Schedulers.boundedElastic] so it never blocks
 * the event loop — this is why `code` is an [AsyncExecutor] even though it does no IO.
 *
 * On evaluator failure [ExpressionEvaluator.evalItems] returns the input maps unchanged; we detect
 * that pass-through and route the `error` handle (when an `error` edge exists, else the default) so
 * authors can branch on a broken program, mirroring `httpRequest`'s error semantics.
 */
object CodeNodeExecutor : AsyncExecutor {
    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): Mono<NodeOutput> {
        val code = node.data["code"] as? String ?: ""
        val inputJson = input.items.map { it.json }
        return Mono.fromCallable { ExpressionEvaluator.evalItems(code, inputJson, ctx.varsView.toMap()) }
                .subscribeOn(Schedulers.boundedElastic())
                .map { outJson ->
                    // evalItems returns the input maps unchanged on any guest error; treat the
                    // referentially-identical pass-through as a failure and route the error handle.
                    val failed = outJson === inputJson
                    val items = outJson.map { ExecutionItem(json = it) }
                    val handle = if (failed) EngineFns.errorOrDefaultHandle(node.id, ctx.edges) else null
                    // On failure pass the original items through so the error branch still has data.
                    val emitted = if (failed) input.items else items
                    val output = NodeOutput.handle(handle, emitted)
                    val detail = if (failed) "code -> error" else "code -> ${emitted.size} items"
                    traceStep(ctx, node, handle, detail, input, output)
                    output
                }
    }
}

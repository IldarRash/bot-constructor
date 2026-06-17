package com.example.botconstructor.botapi.engine.connectors

import com.example.botconstructor.botapi.engine.EngineFns
import com.example.botconstructor.botapi.engine.ExecutionItem
import com.example.botconstructor.botapi.engine.HttpCallResult
import com.example.botconstructor.botapi.engine.NodeInput
import com.example.botconstructor.botapi.engine.NodeOutput
import com.example.botconstructor.botapi.engine.RunContext
import com.example.botconstructor.botapi.engine.traceStep
import com.example.botconstructor.botapi.model.dto.FlowNode
import reactor.core.publisher.Mono

/**
 * Shared HTTP path every connector node reuses so they all inherit the exact SSRF-hardened, capped,
 * trace-consistent semantics of the built-in `httpRequest` node — there is one outbound code path,
 * not one per connector. Connectors interpolate/build their own URL, headers and JSON body, then call
 * [postJson]; this helper owns the per-walk request cap, denied-header stripping, the dial, the
 * result shaping/persistence, error-handle routing, and the trace step.
 */
object ConnectorSupport {

    /**
     * Issues a hardened JSON `POST` (verb fixed to `POST`; [verbForTrace] only labels the trace) on
     * behalf of a connector [node] and returns its [NodeOutput].
     *
     * Security/semantics, mirrored verbatim from `HttpRequestExecutor.applyResult`:
     *  - increments `ctx.httpRequests` and, once over [EngineFns.MAX_HTTP_REQUESTS], does **not** dial
     *    — it shapes a failed [HttpCallResult] (status 0) and routes as a failed call;
     *  - strips [EngineFns.DENIED_HEADERS] (case-insensitive) from [headers] before dialing;
     *  - calls `ctx.httpCaller.call("POST", url, headers, jsonBody)`;
     *  - on a 2xx result stores `shape(result)` into the (first) item's `json` under [saveAs] and into
     *    `ctx.varsView[saveAs]` when [saveAs] is non-blank; follows the default handle on 2xx, else
     *    [EngineFns.errorOrDefaultHandle];
     *  - appends a [traceStep].
     *
     * @param shape Maps the raw [HttpCallResult] into the value persisted under [saveAs] (e.g. pluck a
     *   field, wrap the body). Only invoked on success.
     * @param traceUrl A **redacted** label for the trace (defaults to [url]). For connectors whose
     *   `url` embeds a credential (a Telegram bot token in the path, a Slack/Discord webhook secret),
     *   the live URL must NOT reach the trace — `MatchResult.trace` is returned to the API caller in
     *   `MessageResponse`. Pass a redacted constant (e.g. a Telegram URL with the token shown as
     *   `bot[redacted]`) instead of the live URL.
     */
    fun postJson(
            ctx: RunContext,
            node: FlowNode,
            input: NodeInput,
            url: String,
            headers: Map<String, String>,
            jsonBody: String,
            saveAs: String?,
            shape: (HttpCallResult) -> Any?,
            verbForTrace: String = "POST",
            traceUrl: String = url,
    ): Mono<NodeOutput> {
        val item = input.items.firstOrNull() ?: ExecutionItem(ctx.varsView.toMap())

        // Amplification guard: stop dialing once the per-walk cap is hit; treat as a failed call.
        ctx.httpRequests++
        if (ctx.httpRequests > EngineFns.MAX_HTTP_REQUESTS) {
            return Mono.just(applyResult(
                    ctx, node, input, item, saveAs, shape,
                    HttpCallResult(statusCode = 0, body = null, ok = false),
                    "$verbForTrace $traceUrl -> cap exceeded",
            ))
        }

        val safeHeaders = headers.filterKeys { it.trim().lowercase() !in EngineFns.DENIED_HEADERS }
        return ctx.httpCaller.call("POST", url, safeHeaders, jsonBody).map { res ->
            applyResult(ctx, node, input, item, saveAs, shape, res, EngineFns.httpDetail(verbForTrace, traceUrl, res))
        }
    }

    /**
     * Resolves a connector's optional `data.credentialId` into the secret value of field [field]
     * (e.g. `botToken`, `apiKey`, `webhookUrl`), then continues into [dial] with that value:
     *  - **blank `credentialId`** → no credential configured: calls [dial] with [inlineFallback] (the
     *    interpolated inline field), preserving today's behavior;
     *  - **resolves to a secret** → calls [dial] with the secret's [field] value (or `""` if the
     *    typed field is absent), so the credential overrides the inline field;
     *  - **set but unresolvable** (empty resolve, foreign id, or resolver error) → does NOT dial:
     *    shapes a failed [HttpCallResult] (status 0) and routes the node's `error`/default handle, so
     *    the connector never sends an unauthenticated request.
     *
     * The resolved secret flows only into [dial] (which feeds it to the outbound call); it is never
     * placed into the item, trace, or any log here.
     */
    fun withCredential(
            ctx: RunContext,
            node: FlowNode,
            input: NodeInput,
            field: String,
            inlineFallback: String,
            dial: (secretValue: String) -> Mono<NodeOutput>,
    ): Mono<NodeOutput> {
        val credentialId = (node.data["credentialId"] as? String)?.trim().orEmpty()
        if (credentialId.isEmpty()) return dial(inlineFallback)
        return ctx.resolveCredential(credentialId)
                .flatMap { secret -> dial(secret.data[field] ?: "") }
                .switchIfEmpty(Mono.fromSupplier { credentialError(ctx, node, input) })
                .onErrorResume { Mono.fromSupplier { credentialError(ctx, node, input) } }
    }

    /**
     * Builds the failed [NodeOutput] for a set-but-unresolvable `credentialId`: no dial happens, the
     * node routes its `error`/default handle, and the trace records the failure without ever naming
     * the credential id or any secret. Mirrors a failed call so error branches behave consistently.
     */
    private fun credentialError(ctx: RunContext, node: FlowNode, input: NodeInput): NodeOutput {
        val item = input.items.firstOrNull() ?: ExecutionItem(ctx.varsView.toMap())
        val handle = EngineFns.errorOrDefaultHandle(node.id, ctx.edges)
        val output = NodeOutput.handle(handle, listOf(item))
        traceStep(ctx, node, handle, "credential unresolved", input, output)
        return output
    }

    /**
     * Stores `shape(res)` into the item's `json`/`varsView` (when [saveAs] is set and the call
     * succeeded), records the trace step, and returns the item on the chosen handle (default on 2xx,
     * else `error`/default). Mirrors `HttpRequestExecutor.applyResult` so connectors and the built-in
     * HTTP node stay byte-for-byte consistent in their routing and trace behavior.
     */
    private fun applyResult(
            ctx: RunContext,
            node: FlowNode,
            input: NodeInput,
            item: ExecutionItem,
            saveAs: String?,
            shape: (HttpCallResult) -> Any?,
            res: HttpCallResult,
            detail: String,
    ): NodeOutput {
        val outItem = if (res.ok && !saveAs.isNullOrEmpty()) {
            val shaped = shape(res)
            ctx.varsView[saveAs] = shaped
            item.withJson(mapOf(saveAs to shaped))
        } else {
            item
        }
        val handle = if (res.ok) null else EngineFns.errorOrDefaultHandle(node.id, ctx.edges)
        val output = NodeOutput.handle(handle, listOf(outItem))
        traceStep(ctx, node, handle, detail, input, output)
        return output
    }
}

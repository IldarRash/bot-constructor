package com.example.botconstructor.botapi.engine.connectors

import com.example.botconstructor.botapi.engine.AsyncExecutor
import com.example.botconstructor.botapi.engine.EngineFns
import com.example.botconstructor.botapi.engine.ExecutionItem
import com.example.botconstructor.botapi.engine.HttpCallResult
import com.example.botconstructor.botapi.engine.NodeInput
import com.example.botconstructor.botapi.engine.NodeOutput
import com.example.botconstructor.botapi.engine.RunContext
import com.example.botconstructor.botapi.model.dto.FlowNode
import com.fasterxml.jackson.databind.ObjectMapper
import reactor.core.publisher.Mono

/**
 * `telegramSend` connector — posts a message to a chat via the Telegram Bot API `sendMessage`
 * endpoint. Interpolates its configured fields against the first input item's variables (so
 * `{{expressions}}` resolve), builds the `sendMessage` JSON body, then delegates the actual dial to
 * [ConnectorSupport.postJson] so it inherits the engine's SSRF hardening, per-walk request cap,
 * denied-header stripping, trace step, and `error`/default handle routing — there is no
 * connector-specific HTTP code path.
 *
 * Real API contract (Telegram Bot API `sendMessage`):
 *  - `POST https://api.telegram.org/bot{botToken}/sendMessage` (the bot token is interpolated into the
 *    path);
 *  - `Content-Type: application/json`;
 *  - body `{ "chat_id": <chatId>, "text": <text>, "parse_mode": "HTML" }`.
 *
 * Credentials: when `data.credentialId` is set the resolved `telegramApi.botToken` replaces the
 * inline `botToken`; the inline field is the fallback when no credential is referenced.
 *
 * Response shaping: on a 2xx the parsed body is stored under `saveAs` (default `telegram`). For
 * convenience the connector also surfaces `<saveAs>.ok` and, when present, `<saveAs>.messageId`
 * (lifted from `result.message_id`), so downstream nodes can branch on success or reference the sent
 * message id via dotted-path interpolation (`{{telegram.messageId}}`).
 */
object TelegramConnector : AsyncExecutor {

    /** Project Jackson mapper used to serialize the request body with correct JSON escaping/typing. */
    private val JSON = ObjectMapper()

    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): Mono<NodeOutput> {
        val saveAs = ((node.data["saveAs"] as? String)?.trim().takeUnless { it.isNullOrEmpty() }) ?: "telegram"
        val item = input.items.firstOrNull() ?: ExecutionItem(ctx.varsView.toMap())
        val vars = ctx.itemVars(item)

        val inlineToken = EngineFns.interpolate(node.data["botToken"] as? String ?: "", vars)
        val chatId = EngineFns.interpolate(node.data["chatId"] as? String ?: "", vars)
        val text = EngineFns.interpolate(node.data["text"] as? String ?: "", vars)

        // chat_id is sent as a string (Telegram accepts numeric ids and @channel usernames alike);
        // building via a Map + Jackson guarantees correct escaping of `text` and valid JSON typing.
        val jsonBody = JSON.writeValueAsString(
                linkedMapOf(
                        "chat_id" to chatId,
                        "text" to text,
                        "parse_mode" to "HTML",
                ),
        )

        // When `data.credentialId` is set, the resolved telegramApi.botToken overrides the inline
        // field; a set-but-unresolvable id routes the error handle without dialing.
        return ConnectorSupport.withCredential(ctx, node, input, "botToken", inlineToken) { botToken ->
            ConnectorSupport.postJson(
                    ctx = ctx,
                    node = node,
                    input = input,
                    url = "https://api.telegram.org/bot$botToken/sendMessage",
                    headers = mapOf("Content-Type" to "application/json"),
                    jsonBody = jsonBody,
                    saveAs = saveAs,
                    shape = ::shapeResponse,
                    verbForTrace = "TELEGRAM",
                    // The live URL embeds the bot token in its path; the trace is returned to the API
                    // caller, so label it with a redacted URL — never the credential.
                    traceUrl = "https://api.telegram.org/bot***/sendMessage",
            )
        }
    }

    /**
     * Maps the raw [HttpCallResult] into the value persisted under `saveAs`: the parsed response body
     * with two convenience keys layered on top — `ok` (the call succeeded) and, when the Telegram
     * envelope carries one, `messageId` (`result.message_id`). When the body is a JSON object its keys
     * are preserved and the convenience keys are merged over it; otherwise the body is wrapped under
     * `body` so the convenience keys remain reachable.
     */
    private fun shapeResponse(res: HttpCallResult): Any {
        val base: Map<String, Any?> = (res.body as? Map<*, *>)
                ?.entries
                ?.associate { (k, v) -> k.toString() to v }
                ?: mapOf("body" to res.body)

        val shaped = linkedMapOf<String, Any?>()
        shaped.putAll(base)
        shaped["ok"] = res.ok
        messageId(res.body)?.let { shaped["messageId"] = it }
        return shaped
    }

    /** Lifts `result.message_id` out of the Telegram `sendMessage` envelope, or null when absent. */
    private fun messageId(body: Any?): Any? =
            ((body as? Map<*, *>)?.get("result") as? Map<*, *>)?.get("message_id")
}

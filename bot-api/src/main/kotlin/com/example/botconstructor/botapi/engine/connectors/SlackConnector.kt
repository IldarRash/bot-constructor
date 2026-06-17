package com.example.botconstructor.botapi.engine.connectors

import com.example.botconstructor.botapi.engine.AsyncExecutor
import com.example.botconstructor.botapi.engine.EngineFns
import com.example.botconstructor.botapi.engine.ExecutionItem
import com.example.botconstructor.botapi.engine.NodeInput
import com.example.botconstructor.botapi.engine.NodeOutput
import com.example.botconstructor.botapi.engine.RunContext
import com.example.botconstructor.botapi.model.dto.FlowNode
import com.fasterxml.jackson.databind.ObjectMapper
import reactor.core.publisher.Mono

/**
 * `slackSend` connector — posts a message to Slack via an Incoming Webhook (the simplest real Slack
 * integration; no OAuth, just the secret webhook URL the workspace admin generates). It interpolates
 * `webhookUrl`/`text` against the first input item, builds the `{ "text": … }` JSON body, and
 * delegates the dial to [ConnectorSupport.postJson] so it inherits the exact SSRF-hardened, per-walk
 * capped, trace-consistent outbound path of the built-in `httpRequest` node.
 *
 * Real API contract (Slack Incoming Webhook):
 *  - `POST {webhookUrl}` with `Content-Type: application/json` and body `{ "text": <text> }`.
 *  - Slack answers with the literal string `"ok"` (not JSON) on success, so the body is unusable as a
 *    result; any 2xx is treated as success and `{ ok: true }` is stored under `saveAs` (default
 *    `"slack"`), routing the default handle. A non-2xx (or capped/blocked) call routes `error`.
 *
 * Credentials: when `data.credentialId` is set the resolved `slackWebhook.webhookUrl` replaces the
 * inline `webhookUrl`; the inline field is the fallback when no credential is referenced.
 */
object SlackConnector : AsyncExecutor {

    /** Project Jackson mapper; used only to escape the `text` into a valid JSON string body. */
    private val JSON = ObjectMapper()

    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): Mono<NodeOutput> {
        val item = input.items.firstOrNull() ?: ExecutionItem(ctx.varsView.toMap())
        val vars = ctx.itemVars(item)

        val inlineWebhookUrl = EngineFns.interpolate(node.data["webhookUrl"] as? String ?: "", vars)
        val text = EngineFns.interpolate(node.data["text"] as? String ?: "", vars)
        val saveAs = (node.data["saveAs"] as? String)?.trim()?.ifEmpty { null } ?: "slack"

        val jsonBody = JSON.writeValueAsString(mapOf("text" to text))

        // When `data.credentialId` is set, the resolved slackWebhook.webhookUrl overrides the inline
        // field; a set-but-unresolvable id routes the error handle without dialing.
        return ConnectorSupport.withCredential(ctx, node, input, "webhookUrl", inlineWebhookUrl) { webhookUrl ->
            ConnectorSupport.postJson(
                    ctx = ctx,
                    node = node,
                    input = input,
                    url = webhookUrl,
                    headers = mapOf("Content-Type" to "application/json"),
                    jsonBody = jsonBody,
                    saveAs = saveAs,
                    // Slack returns the bare string "ok", not JSON — the only useful signal is the 2xx
                    // status the helper already gates on, so success persists a fixed { ok: true }.
                    shape = { mapOf("ok" to true) },
                    verbForTrace = "SLACK",
                    // The webhook URL is itself the secret; keep it out of the client-visible trace.
                    traceUrl = "https://hooks.slack.com/services/***",
            )
        }
    }
}

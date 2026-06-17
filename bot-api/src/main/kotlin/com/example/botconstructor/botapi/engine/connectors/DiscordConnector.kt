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
 * `discordSend` connector — posts a message to a Discord channel through an
 * [Incoming Webhook](https://discord.com/developers/docs/resources/webhook#execute-webhook).
 *
 * Real API contract:
 *  - `POST {webhookUrl}` where `webhookUrl` is the full `https://discord.com/api/webhooks/...` URL;
 *  - header `Content-Type: application/json` (added by [ConnectorSupport]'s caller);
 *  - body `{ "content": <content> }`.
 *
 * Discord answers `204 No Content` on success, so there is no body to parse — any `2xx` is treated as
 * success and `{ ok: true }` is stored under `saveAs` (default `discord`). All outbound semantics
 * (per-walk request cap, denied-header stripping, error-handle routing, trace step) are inherited by
 * delegating to [ConnectorSupport.postJson]; this executor only interpolates and shapes the request.
 *
 * Configured `data` fields: `webhookUrl`, `content`, `saveAs`. The `webhookUrl` and `content` strings
 * support `{{expression}}` interpolation against the first input item's vars.
 *
 * Credentials: when `data.credentialId` is set the resolved `discordWebhook.webhookUrl` replaces the
 * inline `webhookUrl`; the inline field is the fallback when no credential is referenced.
 */
object DiscordConnector : AsyncExecutor {

    private val mapper = ObjectMapper()

    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): Mono<NodeOutput> {
        val item = input.items.firstOrNull() ?: ExecutionItem(ctx.varsView.toMap())
        val vars = ctx.itemVars(item)

        val inlineWebhookUrl = EngineFns.interpolate(node.data["webhookUrl"] as? String ?: "", vars)
        val content = EngineFns.interpolate(node.data["content"] as? String ?: "", vars)
        val saveAs = (node.data["saveAs"] as? String)?.trim()

        // Jackson builds the JSON so `content` is escaped correctly regardless of its characters.
        val jsonBody = mapper.writeValueAsString(mapOf("content" to content))

        // When `data.credentialId` is set, the resolved discordWebhook.webhookUrl overrides the inline
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
                    // 204 No Content on success: there is no body to read, so report a flat ok marker.
                    shape = { mapOf("ok" to true) },
                    verbForTrace = "DISCORD",
                    // The webhook URL is itself the secret; keep it out of the client-visible trace.
                    traceUrl = "https://discord.com/api/webhooks/***",
            )
        }
    }
}

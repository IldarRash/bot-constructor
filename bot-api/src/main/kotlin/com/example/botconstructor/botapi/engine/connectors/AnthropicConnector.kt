package com.example.botconstructor.botapi.engine.connectors

import com.example.botconstructor.botapi.engine.AsyncExecutor
import com.example.botconstructor.botapi.engine.EngineFns
import com.example.botconstructor.botapi.engine.ExecutionItem
import com.example.botconstructor.botapi.engine.HttpCallResult
import com.example.botconstructor.botapi.engine.NodeInput
import com.example.botconstructor.botapi.engine.NodeOutput
import com.example.botconstructor.botapi.engine.RunContext
import com.example.botconstructor.botapi.model.dto.FlowNode
import reactor.core.publisher.Mono

/**
 * `anthropicMessage` connector — calls the **Anthropic Messages API** (Claude) and stores the
 * assistant's reply for downstream nodes.
 *
 * It interpolates its configured fields against the first input item, builds the JSON request body
 * itself (so `max_tokens` stays a real JSON number), and delegates the dial to
 * [ConnectorSupport.postJson] so it inherits the exact SSRF-hardened, per-walk-capped, trace- and
 * error-routing semantics of the built-in `httpRequest` node — no connector re-implements HTTP.
 *
 * Wire contract (implemented verbatim):
 *  - `POST https://api.anthropic.com/v1/messages`
 *  - headers `x-api-key: <apiKey>`, `anthropic-version: 2023-06-01`, `content-type: application/json`
 *  - body `{ "model": <model>, "max_tokens": <int>, "messages": [{ "role": "user", "content": <prompt> }] }`
 *  - the assistant text lives at `body.content[0].text`.
 *
 * On a 2xx the value stored under `saveAs` (default `"ai"`) is `{ text: <assistantText>, raw: <body> }`,
 * so a later node reads `{{ai.text}}` for the reply and `{{ai.raw...}}` for anything else.
 *
 * Credentials: when `data.credentialId` is set the resolved `anthropicApi.apiKey` replaces the inline
 * `apiKey` (sent as the `x-api-key` header); the inline field is the fallback when none is referenced.
 */
object AnthropicConnector : AsyncExecutor {

    /** Endpoint of the Anthropic Messages API. */
    private const val URL = "https://api.anthropic.com/v1/messages"

    /** API version header value the Messages API requires. */
    private const val ANTHROPIC_VERSION = "2023-06-01"

    /** Latest Claude model — used when the node leaves `model` blank. */
    private const val DEFAULT_MODEL = "claude-opus-4-8"

    /** Output-token ceiling used when the node leaves `maxTokens` blank or unparseable. */
    private const val DEFAULT_MAX_TOKENS = 1024

    /** Variable name the shaped result is stored under when `saveAs` is blank. */
    private const val DEFAULT_SAVE_AS = "ai"

    private val JSON = com.fasterxml.jackson.databind.ObjectMapper()

    override fun run(node: FlowNode, input: NodeInput, ctx: RunContext): Mono<NodeOutput> {
        val item = input.items.firstOrNull() ?: ExecutionItem(ctx.varsView.toMap())
        val vars = ctx.itemVars(item)

        val inlineApiKey = EngineFns.interpolate(node.data["apiKey"] as? String ?: "", vars)
        val model = EngineFns.interpolate(node.data["model"] as? String ?: "", vars)
                .ifBlank { DEFAULT_MODEL }
        val prompt = EngineFns.interpolate(node.data["prompt"] as? String ?: "", vars)
        val maxTokens = EngineFns.interpolate(node.data["maxTokens"] as? String ?: "", vars)
                .trim().toIntOrNull() ?: DEFAULT_MAX_TOKENS
        val saveAs = ((node.data["saveAs"] as? String)?.trim()).let {
            if (it.isNullOrEmpty()) DEFAULT_SAVE_AS else it
        }

        // `max_tokens` is serialized as a real JSON number; Jackson handles escaping of the strings.
        val body = JSON.writeValueAsString(
                mapOf(
                        "model" to model,
                        "max_tokens" to maxTokens,
                        "messages" to listOf(mapOf("role" to "user", "content" to prompt)),
                ),
        )

        // When `data.credentialId` is set, the resolved anthropicApi.apiKey overrides the inline
        // field; a set-but-unresolvable id routes the error handle without dialing.
        return ConnectorSupport.withCredential(ctx, node, input, "apiKey", inlineApiKey) { apiKey ->
            ConnectorSupport.postJson(
                    ctx = ctx,
                    node = node,
                    input = input,
                    url = URL,
                    headers = mapOf(
                            "x-api-key" to apiKey,
                            "anthropic-version" to ANTHROPIC_VERSION,
                            "content-type" to "application/json",
                    ),
                    jsonBody = body,
                    saveAs = saveAs,
                    shape = ::shape,
                    verbForTrace = "anthropicMessage",
            )
        }
    }

    /**
     * Maps the raw Anthropic response into `{ text, raw }`. The assistant text lives at
     * `body.content[0].text` (`content` is a list of blocks; the first block's `"text"`); a missing
     * or unexpectedly-shaped body yields an empty `text` while `raw` always preserves the full body.
     */
    private fun shape(res: HttpCallResult): Map<String, Any?> {
        val body = res.body
        val content = (body as? Map<*, *>)?.get("content") as? List<*>
        val first = content?.firstOrNull() as? Map<*, *>
        val text = first?.get("text") as? String ?: ""
        return mapOf("text" to text, "raw" to body)
    }
}

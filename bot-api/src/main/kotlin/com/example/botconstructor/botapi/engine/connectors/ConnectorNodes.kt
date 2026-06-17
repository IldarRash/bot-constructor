package com.example.botconstructor.botapi.engine.connectors

import com.example.botconstructor.botapi.engine.NodeExecutor

/**
 * Extension seam for connector node types (Telegram, Slack, Anthropic, Discord, …). Connectors
 * register their [NodeExecutor]s here, each implemented in its own file, so adding a connector never
 * edits `WorkflowEngine`. The engine merges this map under the built-ins (built-ins win on key
 * collision), keeping the core node types authoritative.
 *
 * Each connector object delegates its outbound HTTP to [ConnectorSupport.postJson], so every entry
 * below inherits the SSRF-hardened caller, the per-walk HTTP request cap, denied-header stripping,
 * 2xx-vs-`error`-handle routing, and the trace step.
 *
 * Note: the `code` node is a core built-in registered directly in `WorkflowEngine` and is
 * intentionally NOT listed here.
 */
object ConnectorNodes {
    val executors: Map<String, NodeExecutor> = mapOf(
            "telegramSend" to TelegramConnector,
            "anthropicMessage" to AnthropicConnector,
            "slackSend" to SlackConnector,
            "discordSend" to DiscordConnector,
    )
}

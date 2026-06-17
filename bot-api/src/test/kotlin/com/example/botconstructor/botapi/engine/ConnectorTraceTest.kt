package com.example.botconstructor.botapi.engine

import com.example.botconstructor.botapi.model.dto.FlowEdge
import com.example.botconstructor.botapi.model.dto.FlowNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

/**
 * Security regression: connector nodes whose URL embeds a credential (Telegram bot token, Slack /
 * Discord webhook secret) must NOT leak that credential into the execution trace, which is returned
 * to the API caller in `MessageResponse.trace`.
 */
class ConnectorTraceTest {

    private val fallback = "Sorry, I did not understand"

    @Test
    fun `telegram connector redacts the bot token from the trace detail`() {
        val secret = "123456789:SUPER_SECRET_TOKEN"
        var dialedUrl: String? = null
        val http = HttpCaller { _, url, _, _ ->
            dialedUrl = url
            Mono.just(HttpCallResult(statusCode = 200, body = mapOf("ok" to true), ok = true))
        }
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("tg", "telegramSend", data = mapOf(
                        "botToken" to secret,
                        "chatId" to "42",
                        "text" to "hi",
                        "saveAs" to "telegram",
                )),
        )
        val edges = listOf(FlowEdge("e1", "trigger", "tg"))

        val result = WorkflowEngine.run("hi", nodes, edges, fallback, http).block()!!

        val tgStep = result.trace.single { it.nodeId == "tg" }
        // The real call still carries the token (so the API works)…
        assertThat(dialedUrl).contains(secret)
        // …but the client-visible trace shows only the redacted label.
        assertThat(tgStep.detail).contains("bot***").doesNotContain(secret)
    }
}

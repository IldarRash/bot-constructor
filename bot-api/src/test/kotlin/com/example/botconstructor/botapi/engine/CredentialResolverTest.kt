package com.example.botconstructor.botapi.engine

import com.example.botconstructor.botapi.model.dto.FlowEdge
import com.example.botconstructor.botapi.model.dto.FlowNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Mono

/**
 * Credential-injection + secret-leak invariants. A node opts into a stored credential with
 * `data.credentialId`; the resolved secret must reach ONLY the outbound HTTP call (dialed URL /
 * header), and must NEVER surface in `MatchResult.trace`, `nodeTraces`, or `vars`. A set-but-
 * unresolvable id must route the node's error handle instead of dialing unauthenticated.
 */
class CredentialResolverTest {

    private val fallback = "Sorry, I did not understand"

    /** Asserts the secret never lands in the client-visible projections of a walk. */
    private fun assertNoLeak(result: MatchResult, secret: String) {
        assertThat(result.trace.toString()).doesNotContain(secret)
        result.nodeTraces.forEach { trace ->
            assertThat(trace.detail ?: "").doesNotContain(secret)
            val items = trace.inputItems + trace.outputs.values.flatten()
            items.forEach { item -> assertThat(item.json.toString()).doesNotContain(secret) }
        }
        assertThat(result.vars.toString()).doesNotContain(secret)
    }

    @Test
    fun `telegram connector dials the resolved botToken without leaking it into trace or vars`() {
        val secret = "999888:RESOLVED_BOT_TOKEN"
        var dialedUrl: String? = null
        val http = HttpCaller { _, url, _, _ ->
            dialedUrl = url
            Mono.just(HttpCallResult(statusCode = 200, body = mapOf("ok" to true), ok = true))
        }
        val resolver = CredentialResolver { id ->
            if (id == "cred-tg") Mono.just(CredentialSecret("telegramApi", mapOf("botToken" to secret)))
            else Mono.empty()
        }
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("tg", "telegramSend", data = mapOf(
                        "credentialId" to "cred-tg",
                        "botToken" to "INLINE_SHOULD_BE_IGNORED",
                        "chatId" to "42",
                        "text" to "hi",
                )),
        )
        val edges = listOf(FlowEdge("e1", "trigger", "tg"))

        val result = WorkflowEngine.run("hi", nodes, edges, fallback, http, credentialResolver = resolver).block()!!

        // The outbound call carries the RESOLVED secret (inline field overridden)…
        assertThat(dialedUrl).contains(secret)
        // …and the secret never leaks into any client-visible projection.
        assertNoLeak(result, secret)
    }

    @Test
    fun `connector with a set but unresolvable credentialId routes the error handle and never dials`() {
        var dialed = false
        val http = HttpCaller { _, _, _, _ ->
            dialed = true
            Mono.just(HttpCallResult(statusCode = 200, body = null, ok = true))
        }
        val resolver = CredentialResolver { Mono.empty() } // every id unresolvable
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("tg", "telegramSend", data = mapOf(
                        "credentialId" to "missing",
                        "botToken" to "INLINE",
                        "chatId" to "1",
                        "text" to "hi",
                )),
                FlowNode("err", "sendMessage", data = mapOf("text" to "creds failed")),
        )
        val edges = listOf(
                FlowEdge("e1", "trigger", "tg"),
                FlowEdge("e2", "tg", "err", sourceHandle = "error"),
        )

        val result = WorkflowEngine.run("hi", nodes, edges, fallback, http, credentialResolver = resolver).block()!!

        assertThat(dialed).isFalse()
        assertThat(result.reply).isEqualTo("creds failed")
        val tgStep = result.trace.single { it.nodeId == "tg" }
        assertThat(tgStep.handle).isEqualTo("error")
    }

    @Test
    fun `httpRequest injects a bearer credential header without leaking the token`() {
        val token = "SECRET_BEARER_TOKEN"
        var sentHeaders: Map<String, String> = emptyMap()
        val http = HttpCaller { _, _, headers, _ ->
            sentHeaders = headers
            Mono.just(HttpCallResult(statusCode = 200, body = mapOf("done" to true), ok = true))
        }
        val resolver = CredentialResolver { id ->
            if (id == "cred-bearer") Mono.just(CredentialSecret("httpBearerAuth", mapOf("token" to token)))
            else Mono.empty()
        }
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("http", "httpRequest", data = mapOf(
                        "method" to "GET",
                        "url" to "https://example.com/api",
                        "credentialId" to "cred-bearer",
                        "saveAs" to "resp",
                )),
        )
        val edges = listOf(FlowEdge("e1", "trigger", "http"))

        val result = WorkflowEngine.run("hi", nodes, edges, fallback, http, credentialResolver = resolver).block()!!

        assertThat(sentHeaders["Authorization"]).isEqualTo("Bearer $token")
        assertNoLeak(result, token)
    }

    @Test
    fun `httpRequest injects a header-auth credential header`() {
        val secret = "HEADER_AUTH_SECRET"
        var sentHeaders: Map<String, String> = emptyMap()
        val http = HttpCaller { _, _, headers, _ ->
            sentHeaders = headers
            Mono.just(HttpCallResult(statusCode = 200, body = null, ok = true))
        }
        val resolver = CredentialResolver {
            Mono.just(CredentialSecret("httpHeaderAuth", mapOf("headerName" to "X-Api-Key", "headerValue" to secret)))
        }
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("http", "httpRequest", data = mapOf(
                        "method" to "GET",
                        "url" to "https://example.com/api",
                        "credentialId" to "cred-header",
                )),
        )
        val edges = listOf(FlowEdge("e1", "trigger", "http"))

        val result = WorkflowEngine.run("hi", nodes, edges, fallback, http, credentialResolver = resolver).block()!!

        assertThat(sentHeaders["X-Api-Key"]).isEqualTo(secret)
        assertNoLeak(result, secret)
    }

    @Test
    fun `connector with no credentialId still uses the inline field as the fallback`() {
        val inline = "INLINE_TOKEN"
        var dialedUrl: String? = null
        val http = HttpCaller { _, url, _, _ ->
            dialedUrl = url
            Mono.just(HttpCallResult(statusCode = 200, body = mapOf("ok" to true), ok = true))
        }
        // Resolver must never be consulted when no credentialId is set.
        val resolver = CredentialResolver { throw AssertionError("resolver must not be called") }
        val nodes = listOf(
                FlowNode("trigger", "trigger"),
                FlowNode("tg", "telegramSend", data = mapOf(
                        "botToken" to inline,
                        "chatId" to "1",
                        "text" to "hi",
                )),
        )
        val edges = listOf(FlowEdge("e1", "trigger", "tg"))

        WorkflowEngine.run("hi", nodes, edges, fallback, http, credentialResolver = resolver).block()!!

        assertThat(dialedUrl).contains(inline)
    }
}

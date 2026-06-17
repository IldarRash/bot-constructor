package com.example.botconstructor.botapi.runtime

import com.example.botconstructor.botapi.client.ClientApiClient
import com.example.botconstructor.botapi.engine.CredentialSecret
import com.example.botconstructor.botapi.engine.HttpCallResult
import com.example.botconstructor.botapi.engine.HttpCaller
import com.example.botconstructor.botapi.model.dto.BotSummary
import com.example.botconstructor.botapi.model.dto.FlowEdge
import com.example.botconstructor.botapi.model.dto.FlowNode
import com.example.botconstructor.botapi.model.dto.ExecutionRecordRequest
import com.example.botconstructor.botapi.model.dto.ManualRunRequest
import com.example.botconstructor.botapi.model.dto.WebhookRequest
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class RuntimeServiceTest {

    /** Fails any test that unexpectedly dials HTTP. */
    private val noHttp = HttpCaller { _, _, _, _ ->
        throw AssertionError("no HTTP call expected in this test")
    }

    private val mapper = ObjectMapper()

    private fun bot(): BotSummary = BotSummary(
            id = "b1",
            name = "Greeter",
            type = "web",
            nodes = listOf(
                    FlowNode("trigger", "trigger"),
                    FlowNode("kw", "keyword", data = mapOf("label" to "Greeting", "keyWords" to listOf("hi"))),
                    FlowNode("msg", "sendMessage", data = mapOf("text" to "Hello {{caller}}!")),
            ),
            edges = listOf(
                    FlowEdge("e1", "trigger", "kw"),
                    FlowEdge("e2", "kw", "msg", sourceHandle = "match"),
            ),
            fallbackAnswer = "Sorry, I did not understand",
    )

    @Test
    fun `runWebhook resolves the bot by token, runs the flow, and returns the reply`() {
        val clientApiClient = mockk<ClientApiClient>()
        every { clientApiClient.fetchBotByWebhook("tok123") } returns Mono.just(bot())
        every { clientApiClient.postExecution(any()) } returns Mono.empty()
        val service = RuntimeService(clientApiClient, noHttp, mapper)

        val result = service.runWebhook("tok123", WebhookRequest(message = "hi there", vars = mapOf("caller" to "Ada")))

        StepVerifier.create(result)
                .assertNext { resp ->
                    assert(resp.reply == "Hello Ada!") { "expected interpolated reply, got ${resp.reply}" }
                    assert(resp.matched?.text == "Greeting")
                    assert(resp.vars["message"] == "hi there")
                    assert(resp.vars["caller"] == "Ada")
                }
                .verifyComplete()
    }

    @Test
    fun `runWebhook with a null message falls back when nothing matches`() {
        val clientApiClient = mockk<ClientApiClient>()
        every { clientApiClient.fetchBotByWebhook("tok123") } returns Mono.just(bot())
        every { clientApiClient.postExecution(any()) } returns Mono.empty()
        val service = RuntimeService(clientApiClient, noHttp, mapper)

        val result = service.runWebhook("tok123", WebhookRequest())

        StepVerifier.create(result)
                .assertNext { resp -> assert(resp.reply == "Sorry, I did not understand") }
                .verifyComplete()
    }

    /** A bot that fans an input array out into one item per element via `splitOut`. */
    private fun splitBot(): BotSummary = BotSummary(
            id = "split1",
            name = "Splitter",
            type = "web",
            nodes = listOf(
                    FlowNode("trigger", "trigger"),
                    FlowNode("split", "splitOut", data = mapOf("field" to "nums")),
                    FlowNode("msg", "sendMessage", data = mapOf("text" to "ok")),
            ),
            edges = listOf(
                    FlowEdge("e1", "trigger", "split"),
                    FlowEdge("e2", "split", "msg"),
            ),
            fallbackAnswer = "fallback",
    )

    @Test
    fun `runWebhook posts a rich execution record and caps node items at 20 with a truncation marker`() {
        val clientApiClient = mockk<ClientApiClient>()
        every { clientApiClient.fetchBotByWebhook("tok") } returns Mono.just(splitBot())
        val captured = slot<ExecutionRecordRequest>()
        every { clientApiClient.postExecution(capture(captured)) } returns Mono.empty()
        val service = RuntimeService(clientApiClient, noHttp, mapper)

        // 25 elements -> splitOut emits 25 output items -> capping keeps 20 + a truncation marker.
        val nums = (0 until 25).toList()
        val result = service.runWebhook("tok", WebhookRequest(message = "go", vars = mapOf("nums" to nums)))

        // sendMessage fires once per input item (25 items), so the reply is "ok" repeated.
        StepVerifier.create(result)
                .assertNext { resp -> assert(resp.reply.startsWith("ok")) { "expected the sendMessage reply, got ${resp.reply}" } }
                .verifyComplete()

        verify { clientApiClient.postExecution(any()) }
        val record = captured.captured
        assert(record.botId == "split1")
        assert(record.status == "success") { "expected success, got ${record.status}" }
        assert(record.trigger == "webhook") { "expected webhook trigger, got ${record.trigger}" }
        assert(record.message == "go")
        assert(record.reply.startsWith("ok"))

        val split = record.nodes.first { it.nodeId == "split" }
        // The null output handle is serialized as the literal "default" key.
        val defaultOut = split.outputs["default"] ?: error("expected a default output bucket")
        assert(defaultOut.size == 21) { "expected 20 items + 1 truncation marker, got ${defaultOut.size}" }
        // The first 20 carry real item data (the exploded array element under numsItem).
        assert(defaultOut[0].json["numsItem"] == 0)
        // The 21st item is the truncation marker carrying the omitted count (25 - 20 = 5).
        assert(defaultOut[20].json["__truncated__"] == 5) {
            "expected truncation marker with 5 omitted, got ${defaultOut[20].json}"
        }
    }

    @Test
    fun `runWebhook bounds a fat item's json with a truncated-bytes marker`() {
        val clientApiClient = mockk<ClientApiClient>()
        every { clientApiClient.fetchBotByWebhook("tok") } returns Mono.just(bot())
        val captured = slot<ExecutionRecordRequest>()
        every { clientApiClient.postExecution(capture(captured)) } returns Mono.empty()
        val service = RuntimeService(clientApiClient, noHttp, mapper)

        // A single attacker-controlled webhook var well over the 32 KB per-item budget flows into the
        // trigger seed item json; it must be replaced by the truncated-bytes marker, not persisted whole.
        val fat = "x".repeat(64 * 1024)
        val result = service.runWebhook("tok", WebhookRequest(message = "hi", vars = mapOf("blob" to fat)))

        StepVerifier.create(result).assertNext { }.verifyComplete()

        val record = captured.captured
        val items = record.nodes.flatMap { it.inputItems + it.outputs.values.flatten() }
        val markers = items.filter { it.json.containsKey("__truncated_bytes__") }
        assert(markers.isNotEmpty()) { "expected at least one truncated-bytes marker for the fat blob" }
        // No persisted item carries the raw 64 KB blob.
        assert(items.none { it.json["blob"] == fat }) { "fat blob must not be persisted verbatim" }
    }

    /** A webhook bot whose connector opts into a stored credential by id. */
    private fun credentialBot(): BotSummary = BotSummary(
            id = "cbot",
            name = "Creds",
            type = "web",
            nodes = listOf(
                    FlowNode("trigger", "trigger"),
                    FlowNode("tg", "telegramSend", data = mapOf(
                            "credentialId" to "cred-1",
                            "chatId" to "42",
                            "text" to "hi",
                    )),
            ),
            edges = listOf(FlowEdge("e1", "trigger", "tg")),
            fallbackAnswer = "fallback",
    )

    @Test
    fun `runWebhook binds the credential resolver to the current bot id and never leaks the secret`() {
        val secret = "RESOLVED:BOT_TOKEN"
        var dialedUrl: String? = null
        val http = HttpCaller { _, url, _, _ ->
            dialedUrl = url
            Mono.just(HttpCallResult(statusCode = 200, body = mapOf("ok" to true), ok = true))
        }
        val clientApiClient = mockk<ClientApiClient>()
        every { clientApiClient.fetchBotByWebhook("tok") } returns Mono.just(credentialBot())
        every { clientApiClient.fetchCredential("cred-1", "cbot") } returns
                Mono.just(CredentialSecret("telegramApi", mapOf("botToken" to secret)))
        val captured = slot<ExecutionRecordRequest>()
        every { clientApiClient.postExecution(capture(captured)) } returns Mono.empty()
        val service = RuntimeService(clientApiClient, http, mapper)

        val result = service.runWebhook("tok", WebhookRequest(message = "hi"))

        StepVerifier.create(result).assertNext { resp ->
            // The secret never reaches the API-visible reply/trace/vars.
            assert(!resp.trace.toString().contains(secret)) { "secret leaked into trace" }
            assert(!resp.vars.toString().contains(secret)) { "secret leaked into vars" }
        }.verifyComplete()

        // Resolver was bound to THIS bot id (anti-IDOR) and the resolved secret reached the dial.
        verify { clientApiClient.fetchCredential("cred-1", "cbot") }
        assert(dialedUrl!!.contains(secret)) { "resolved token must reach the outbound URL" }
        // The persisted execution record never carries the secret.
        val record = captured.captured
        assert(!record.toString().contains(secret)) { "secret leaked into persisted execution record" }
    }

    /** A bot whose only worker node is an httpRequest — used to assert pinning skips the dial. */
    private fun httpBot(): BotSummary = BotSummary(
            id = "hbot",
            name = "Fetcher",
            type = "web",
            nodes = listOf(
                    FlowNode("trigger", "trigger"),
                    FlowNode("http", "httpRequest", data = mapOf(
                            "url" to "https://example.com/data",
                            "method" to "GET",
                            "saveAs" to "resp",
                    )),
                    FlowNode("msg", "sendMessage", data = mapOf("text" to "got {{resp.value}}")),
            ),
            edges = listOf(
                    FlowEdge("e1", "trigger", "http"),
                    FlowEdge("e2", "http", "msg"),
            ),
            fallbackAnswer = "fallback",
    )

    @Test
    fun `runManual is owner-scoped, persists a manual execution, and returns rich per-node data`() {
        val clientApiClient = mockk<ClientApiClient>()
        every { clientApiClient.fetchBot("b1", "Token jwt") } returns Mono.just(bot())
        val captured = slot<ExecutionRecordRequest>()
        every { clientApiClient.postExecution(capture(captured)) } returns Mono.empty()
        val service = RuntimeService(clientApiClient, noHttp, mapper)

        val request = ManualRunRequest(message = "hi", vars = mapOf("caller" to "Ada"))
        val result = service.runManual("b1", "Token jwt", request)

        StepVerifier.create(result)
                .assertNext { resp ->
                    assert(resp.reply == "Hello Ada!") { "expected interpolated reply, got ${resp.reply}" }
                    assert(resp.matched?.text == "Greeting")
                    assert(resp.vars["caller"] == "Ada")
                    // Rich per-node data is projected so the editor inspects input/output inline.
                    val msg = resp.nodes.firstOrNull { it.nodeId == "msg" } ?: error("expected the msg node")
                    assert(msg.inputItems.isNotEmpty()) { "expected the msg node to carry input items" }
                    assert(resp.nodes.any { it.nodeId == "kw" }) { "expected the keyword node in nodes[]" }
                }
                .verifyComplete()

        // Owner-scoped: the bot was loaded forwarding the caller's Authorization header.
        verify { clientApiClient.fetchBot("b1", "Token jwt") }
        // The execution was persisted with the manual trigger.
        assert(captured.captured.trigger == "manual") { "expected manual trigger, got ${captured.captured.trigger}" }
    }

    @Test
    fun `runManual with a pinned node skips its executor and flows the pinned items downstream`() {
        val clientApiClient = mockk<ClientApiClient>()
        every { clientApiClient.fetchBot("hbot", "Token jwt") } returns Mono.just(httpBot())
        every { clientApiClient.postExecution(any()) } returns Mono.empty()
        // noHttp fails the test if the httpRequest node is ever dialed — pinning must prevent that.
        val service = RuntimeService(clientApiClient, noHttp, mapper)

        val request = ManualRunRequest(
                message = "go",
                pinnedData = mapOf("http" to listOf(mapOf("json" to mapOf("resp" to mapOf("value" to 7))))),
        )
        val result = service.runManual("hbot", "Token jwt", request)

        StepVerifier.create(result)
                .assertNext { resp ->
                    // Pinned items flowed into the downstream sendMessage, which interpolated them.
                    assert(resp.reply == "got 7") { "expected pinned value to flow downstream, got ${resp.reply}" }
                    val http = resp.nodes.firstOrNull { it.nodeId == "http" } ?: error("expected the http node")
                    assert(http.detail == "pinned (1 items)") { "expected pinned detail, got ${http.detail}" }
                    // The pinned items are the node's default output.
                    assert(http.outputs["default"]?.firstOrNull()?.json?.get("resp") == mapOf("value" to 7))
                }
                .verifyComplete()
    }

    @Test
    fun `runWebhook surfaces an unknown-token 404 from client-api`() {
        val clientApiClient = mockk<ClientApiClient>()
        val notFound = WebClientResponseException.create(
                HttpStatus.NOT_FOUND.value(), "Not Found", org.springframework.http.HttpHeaders.EMPTY, ByteArray(0), null,
        )
        every { clientApiClient.fetchBotByWebhook("nope") } returns Mono.error(notFound)
        val service = RuntimeService(clientApiClient, noHttp, mapper)

        StepVerifier.create(service.runWebhook("nope", WebhookRequest(message = "hi")))
                .expectErrorMatches { it is WebClientResponseException && it.statusCode == HttpStatus.NOT_FOUND }
                .verify()
    }
}

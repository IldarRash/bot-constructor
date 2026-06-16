package com.example.botconstructor.services

import com.example.botconstructor.dto.BotRequest
import com.example.botconstructor.dto.EdgeRequest
import com.example.botconstructor.dto.NodeRequest
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.model.BotTemplate
import com.example.botconstructor.model.BotType
import com.example.botconstructor.model.FlowEdge
import com.example.botconstructor.model.FlowNode
import com.example.botconstructor.model.Question
import com.example.botconstructor.repos.BotTemplateRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier

class BotServiceTest {

    private val repository = mockk<BotTemplateRepository>()
    private val service = BotService(repository)

    private val ownerId = "owner-1"

    private fun sampleRequest() = BotRequest(
            name = "Support",
            type = BotType.Telegram,
            nodes = listOf(
                    NodeRequest(id = "n1", type = "trigger"),
                    NodeRequest(
                            id = "n2",
                            type = "keyword",
                            data = mapOf("label" to "Hi?", "keyWords" to listOf("hi", "hello")),
                    ),
                    NodeRequest(id = "n3", type = "sendMessage", data = mapOf("text" to "Hello there")),
            ),
            edges = listOf(
                    EdgeRequest(id = "e1", source = "n1", target = "n2"),
                    EdgeRequest(id = "e2", source = "n2", target = "n3", sourceHandle = "match"),
            ),
            fallbackAnswer = "Sorry, I did not understand",
    )

    private fun sampleBot(id: String = "bot-1", owner: String = ownerId) = BotTemplate(
            id = id,
            name = "Support",
            type = BotType.Telegram,
            ownerId = owner,
            nodes = listOf(
                    FlowNode(id = "n1", type = "trigger"),
                    FlowNode(id = "n3", type = "sendMessage", data = mapOf("text" to "Hello there")),
            ),
            edges = listOf(FlowEdge(id = "e1", source = "n1", target = "n3")),
            fallbackAnswer = "Sorry, I did not understand",
    )

    @Test
    fun `create persists a bot owned by the current user`() {
        val saved = slot<BotTemplate>()
        every { repository.save(capture(saved)) } answers { Mono.just(saved.captured) }

        StepVerifier.create(service.create(sampleRequest(), ownerId))
                .assertNext {
                    assertThat(it.name).isEqualTo("Support")
                    assertThat(it.type).isEqualTo("Telegram")
                    assertThat(it.ownerId).isEqualTo(ownerId)
                    assertThat(it.nodes).hasSize(3)
                    assertThat(it.nodes.single { n -> n.type == "trigger" }.id).isEqualTo("n1")
                    assertThat(it.edges.map { e -> e.id }).containsExactly("e1", "e2")
                }
                .verifyComplete()
    }

    @Test
    fun `create generates a non-blank webhook token and persists it`() {
        val saved = slot<BotTemplate>()
        every { repository.save(capture(saved)) } answers { Mono.just(saved.captured) }

        StepVerifier.create(service.create(sampleRequest(), ownerId))
                .assertNext { view ->
                    assertThat(view.webhookToken).isNotBlank()
                    // The persisted document carries the same token (not just the view).
                    assertThat(saved.captured.webhookToken).isEqualTo(view.webhookToken)
                }
                .verifyComplete()
    }

    @Test
    fun `update preserves the existing webhook token and clients cannot change it`() {
        val existing = sampleBot().copy(webhookToken = "original-token")
        val saved = slot<BotTemplate>()
        every { repository.findById("bot-1") } returns Mono.just(existing)
        every { repository.save(capture(saved)) } answers { Mono.just(saved.captured) }

        StepVerifier.create(service.update("bot-1", sampleRequest(), ownerId))
                .assertNext { view ->
                    assertThat(view.webhookToken).isEqualTo("original-token")
                    assertThat(saved.captured.webhookToken).isEqualTo("original-token")
                }
                .verifyComplete()
    }

    @Test
    fun `findByWebhookToken returns the bot for a known token`() {
        every { repository.findByWebhookToken("known-token") } returns
                Mono.just(sampleBot().copy(webhookToken = "known-token"))

        StepVerifier.create(service.findByWebhookToken("known-token"))
                .assertNext {
                    assertThat(it.id).isEqualTo("bot-1")
                    assertThat(it.webhookToken).isEqualTo("known-token")
                }
                .verifyComplete()
    }

    @Test
    fun `findByWebhookToken is empty for an unknown token`() {
        every { repository.findByWebhookToken("unknown") } returns Mono.empty()

        StepVerifier.create(service.findByWebhookToken("unknown"))
                .verifyComplete()
    }

    @Test
    fun `findByWebhookToken rejects a blank token without hitting the repository`() {
        StepVerifier.create(service.findByWebhookToken("  "))
                .verifyComplete()
    }

    @Test
    fun `create accepts a valid cron schedule and persists it`() {
        val saved = slot<BotTemplate>()
        every { repository.save(capture(saved)) } answers { Mono.just(saved.captured) }

        val request = sampleRequest().copy(schedule = "0 0 * * * *")

        StepVerifier.create(service.create(request, ownerId))
                .assertNext { view ->
                    assertThat(view.schedule).isEqualTo("0 0 * * * *")
                    assertThat(saved.captured.schedule).isEqualTo("0 0 * * * *")
                }
                .verifyComplete()
    }

    @Test
    fun `create treats a blank schedule as not scheduled`() {
        val saved = slot<BotTemplate>()
        every { repository.save(capture(saved)) } answers { Mono.just(saved.captured) }

        val request = sampleRequest().copy(schedule = "   ")

        StepVerifier.create(service.create(request, ownerId))
                .assertNext { assertThat(it.schedule).isNull() }
                .verifyComplete()
    }

    @Test
    fun `create rejects an invalid cron schedule`() {
        val request = sampleRequest().copy(schedule = "not a cron")

        StepVerifier.create(service.create(request, ownerId))
                .expectError(InvalidRequestException::class.java)
                .verify()
    }

    @Test
    fun `update rejects an invalid cron schedule`() {
        val request = sampleRequest().copy(schedule = "60 99 * * * *")

        StepVerifier.create(service.update("bot-1", request, ownerId))
                .expectError(InvalidRequestException::class.java)
                .verify()
    }

    @Test
    fun `create rejects a graph without a trigger`() {
        val request = sampleRequest().copy(nodes = sampleRequest().nodes.filter { it.type != "trigger" })

        StepVerifier.create(service.create(request, ownerId))
                .expectError(InvalidRequestException::class.java)
                .verify()
    }

    @Test
    fun `create rejects a graph with multiple triggers`() {
        val request = sampleRequest().copy(
                nodes = sampleRequest().nodes + NodeRequest(id = "n4", type = "trigger"),
        )

        StepVerifier.create(service.create(request, ownerId))
                .expectError(InvalidRequestException::class.java)
                .verify()
    }

    @Test
    fun `create rejects a graph with duplicate node ids`() {
        val request = sampleRequest().copy(
                nodes = sampleRequest().nodes + NodeRequest(id = "n2", type = "sendMessage"),
        )

        StepVerifier.create(service.create(request, ownerId))
                .expectError(InvalidRequestException::class.java)
                .verify()
    }

    @Test
    fun `create rejects an edge that references a nonexistent node`() {
        val request = sampleRequest().copy(
                edges = sampleRequest().edges + EdgeRequest(id = "e3", source = "n3", target = "ghost"),
        )

        StepVerifier.create(service.create(request, ownerId))
                .expectError(InvalidRequestException::class.java)
                .verify()
    }

    @Test
    fun `list returns only the owners bots`() {
        every { repository.findByOwnerId(ownerId) } returns Flux.just(sampleBot("a"), sampleBot("b"))

        StepVerifier.create(service.list(ownerId).collectList())
                .assertNext { assertThat(it.map { v -> v.id }).containsExactly("a", "b") }
                .verifyComplete()
    }

    @Test
    fun `legacy questions are converted into a graph on read`() {
        val legacy = BotTemplate(
                id = "legacy-1",
                name = "Old",
                type = BotType.Telegram,
                ownerId = ownerId,
                fallbackAnswer = "fallback",
                legacyQuestions = listOf(Question("Hi?", listOf("hi", "hello"), "Hello there")),
        )
        every { repository.findById("legacy-1") } returns Mono.just(legacy)

        StepVerifier.create(service.get("legacy-1", ownerId))
                .assertNext {
                    assertThat(it.nodes.map { n -> n.type })
                            .containsExactly("trigger", "keyword", "sendMessage")
                    val keyword = it.nodes.single { n -> n.type == "keyword" }
                    assertThat(keyword.data["label"]).isEqualTo("Hi?")
                    assertThat(keyword.data["keyWords"]).isEqualTo(listOf("hi", "hello"))
                    assertThat(it.nodes.single { n -> n.type == "sendMessage" }.data["text"])
                            .isEqualTo("Hello there")
                    assertThat(it.edges.single { e -> e.sourceHandle == "match" }.source)
                            .isEqualTo(keyword.id)
                }
                .verifyComplete()
    }

    @Test
    fun `get rejects access to another users bot`() {
        every { repository.findById("bot-1") } returns Mono.just(sampleBot(owner = "someone-else"))

        StepVerifier.create(service.get("bot-1", ownerId))
                .expectError(InvalidRequestException::class.java)
                .verify()
    }

    @Test
    fun `get fails when the bot does not exist`() {
        every { repository.findById("missing") } returns Mono.empty()

        StepVerifier.create(service.get("missing", ownerId))
                .expectError(InvalidRequestException::class.java)
                .verify()
    }

    @Test
    fun `update rejects another users bot before saving`() {
        every { repository.findById("bot-1") } returns Mono.just(sampleBot(owner = "someone-else"))

        StepVerifier.create(service.update("bot-1", sampleRequest(), ownerId))
                .expectError(InvalidRequestException::class.java)
                .verify()
    }
}

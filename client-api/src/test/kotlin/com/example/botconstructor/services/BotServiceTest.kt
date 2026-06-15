package com.example.botconstructor.services

import com.example.botconstructor.dto.BotQuestion
import com.example.botconstructor.dto.BotRequest
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.model.BotTemplate
import com.example.botconstructor.model.BotType
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
            questions = listOf(BotQuestion("Hi?", listOf("hi", "hello"), "Hello there")),
            fallbackAnswer = "Sorry, I did not understand",
    )

    private fun sampleBot(id: String = "bot-1", owner: String = ownerId) = BotTemplate(
            id = id,
            name = "Support",
            type = BotType.Telegram,
            ownerId = owner,
            questions = listOf(Question("Hi?", listOf("hi", "hello"), "Hello there")),
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
                    assertThat(it.questions).hasSize(1)
                    assertThat(it.questions[0].keyWords).containsExactly("hi", "hello")
                }
                .verifyComplete()
    }

    @Test
    fun `list returns only the owners bots`() {
        every { repository.findByOwnerId(ownerId) } returns Flux.just(sampleBot("a"), sampleBot("b"))

        StepVerifier.create(service.list(ownerId).collectList())
                .assertNext { assertThat(it.map { v -> v.id }).containsExactly("a", "b") }
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

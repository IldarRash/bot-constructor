package com.example.botconstructor.services

import com.example.botconstructor.dto.ExecutionNodeRecord
import com.example.botconstructor.dto.ExecutionRecordRequest
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.lib.OffsetBasedPageable
import com.example.botconstructor.model.BotTemplate
import com.example.botconstructor.model.BotType
import com.example.botconstructor.model.Execution
import com.example.botconstructor.repos.BotTemplateRepository
import com.example.botconstructor.repos.ExecutionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Pageable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.time.Instant

class ExecutionServiceTest {

    private val executionRepository = mockk<ExecutionRepository>()
    private val botTemplateRepository = mockk<BotTemplateRepository>()
    private val service = ExecutionService(executionRepository, botTemplateRepository)

    private val ownerId = "owner-1"
    private val botId = "bot-1"

    private fun bot(owner: String = ownerId) = BotTemplate(
            id = botId,
            name = "Support",
            type = BotType.Telegram,
            ownerId = owner,
            fallbackAnswer = "fallback",
    )

    private fun request() = ExecutionRecordRequest(
            botId = botId,
            status = "success",
            trigger = "message",
            startedAt = Instant.parse("2026-06-16T10:00:00Z"),
            finishedAt = Instant.parse("2026-06-16T10:00:01Z"),
            message = "hi",
            reply = "hello",
            nodes = listOf(
                    ExecutionNodeRecord(
                            nodeId = "n1",
                            type = "trigger",
                            handle = null,
                            detail = null,
                            outputs = mapOf("default" to listOf(mapOf("json" to mapOf("text" to "hi")))),
                    ),
            ),
    )

    private fun execution(id: String, started: Instant, owner: String = ownerId) = Execution(
            id = id,
            botId = botId,
            ownerId = owner,
            status = "success",
            trigger = "message",
            startedAt = started,
            finishedAt = started.plusSeconds(1),
            message = "hi",
            reply = "hello",
            nodes = emptyList(),
            createdAt = Instant.now(),
    )

    @Test
    fun `persist resolves ownerId from the bot document and saves`() {
        val saved = slot<Execution>()
        every { botTemplateRepository.findById(botId) } returns Mono.just(bot())
        every { executionRepository.save(capture(saved)) } answers { Mono.just(saved.captured) }

        StepVerifier.create(service.persist(request()))
                .assertNext {
                    assertThat(it.ownerId).isEqualTo(ownerId)
                    assertThat(it.botId).isEqualTo(botId)
                    assertThat(saved.captured.ownerId).isEqualTo(ownerId)
                    assertThat(saved.captured.id).isNotBlank()
                    assertThat(saved.captured.nodes.single().outputs["default"]).hasSize(1)
                }
                .verifyComplete()
    }

    @Test
    fun `persist drops the record when the bot is gone`() {
        every { botTemplateRepository.findById(botId) } returns Mono.empty()

        StepVerifier.create(service.persist(request()))
                .verifyComplete()
    }

    @Test
    fun `list is owner-scoped and returns newest-first summaries with paging`() {
        val pageable = slot<Pageable>()
        every { botTemplateRepository.findById(botId) } returns Mono.just(bot())
        every {
            executionRepository.findByBotIdAndOwnerId(botId, ownerId, capture(pageable))
        } returns Flux.just(
                execution("e2", Instant.parse("2026-06-16T11:00:00Z")),
                execution("e1", Instant.parse("2026-06-16T10:00:00Z")),
        )

        StepVerifier.create(service.list(botId, ownerId, limit = 10, offset = 5).collectList())
                .assertNext {
                    assertThat(it.map { v -> v.id }).containsExactly("e2", "e1")
                    assertThat(it.first().nodeCount).isEqualTo(0)
                }
                .verifyComplete()

        assertThat(pageable.captured).isInstanceOf(OffsetBasedPageable::class.java)
        assertThat(pageable.captured.pageSize).isEqualTo(10)
        assertThat(pageable.captured.offset).isEqualTo(5L)
        // Total, stable sort: startedAt desc with id desc as the unique tiebreaker.
        assertThat(pageable.captured.sort.map { it.property to it.direction })
                .containsExactly(
                        "startedAt" to org.springframework.data.domain.Sort.Direction.DESC,
                        "id" to org.springframework.data.domain.Sort.Direction.DESC,
                )
    }

    @Test
    fun `list rejects a bot owned by another user with Bot not found`() {
        every { botTemplateRepository.findById(botId) } returns Mono.just(bot(owner = "someone-else"))

        StepVerifier.create(service.list(botId, ownerId, limit = 10, offset = 0).collectList())
                .expectErrorMatches {
                    it is InvalidRequestException && it.subject == "Bot" && it.violation == "not found"
                }
                .verify()
    }

    @Test
    fun `list rejects a missing bot with Bot not found`() {
        every { botTemplateRepository.findById(botId) } returns Mono.empty()

        StepVerifier.create(service.list(botId, ownerId, limit = 10, offset = 0).collectList())
                .expectError(InvalidRequestException::class.java)
                .verify()
    }

    @Test
    fun `get returns the full execution for its owner`() {
        every { executionRepository.findByIdAndOwnerId("e1", ownerId) } returns
                Mono.just(execution("e1", Instant.parse("2026-06-16T10:00:00Z")))

        StepVerifier.create(service.get("e1", ownerId))
                .assertNext { assertThat(it.id).isEqualTo("e1") }
                .verifyComplete()
    }

    @Test
    fun `get is not found for a non-owner`() {
        // The repo finder is owner-scoped, so a non-owner read completes empty -> opaque not found.
        every { executionRepository.findByIdAndOwnerId("e1", "intruder") } returns Mono.empty()

        StepVerifier.create(service.get("e1", "intruder"))
                .expectErrorMatches {
                    it is InvalidRequestException && it.subject == "Execution" && it.violation == "not found"
                }
                .verify()
    }
}

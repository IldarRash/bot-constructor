package com.example.botconstructor.api

import com.example.botconstructor.config.WebConfig
import com.example.botconstructor.dto.ExecutionRecordRequest
import com.example.botconstructor.dto.ExecutionSummaryView
import com.example.botconstructor.dto.ExecutionView
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.model.Execution
import com.example.botconstructor.model.User
import com.example.botconstructor.services.ExecutionService
import com.example.botconstructor.services.UserSession
import com.example.botconstructor.services.UserSessionProvider
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

class ExecutionHandlerTest {

    private val executionService = mockk<ExecutionService>()
    private val userSessionProvider = mockk<UserSessionProvider>()
    private val userHandler = mockk<UserHandler>(relaxed = true)
    private val botHandler = mockk<BotHandler>(relaxed = true)

    private val ownerId = "owner-1"
    private val executionHandler = ExecutionHandler(executionService, userSessionProvider)
    private val credentialHandler = mockk<CredentialHandler>(relaxed = true)

    private val client = WebTestClient
            .bindToRouterFunction(WebConfig().route(userHandler, botHandler, executionHandler, credentialHandler))
            .build()

    private fun session(): Mono<UserSession> {
        val user = User(
                username = "alice",
                email = "alice@example.com",
                encodedPassword = "hash",
                bio = null,
                image = null,
                id = ownerId,
        )
        return Mono.just(UserSession(user, "token"))
    }

    private fun summary(id: String) = ExecutionSummaryView(
            id = id,
            botId = "bot-1",
            status = "success",
            trigger = "message",
            startedAt = Instant.parse("2026-06-16T10:00:00Z"),
            finishedAt = Instant.parse("2026-06-16T10:00:01Z"),
            message = "hi",
            reply = "hello",
            nodeCount = 2,
    )

    @Test
    fun `internal record persists without a user session`() {
        val captured = slot<ExecutionRecordRequest>()
        every { executionService.persist(capture(captured)) } answers {
            Mono.just(mockk<Execution>(relaxed = true))
        }

        client.post().uri("/api/internal/executions")
                .bodyValue(
                        mapOf(
                                "botId" to "bot-1",
                                "status" to "success",
                                "trigger" to "message",
                                "startedAt" to "2026-06-16T10:00:00Z",
                                "finishedAt" to "2026-06-16T10:00:01Z",
                                "message" to "hi",
                                "reply" to "hello",
                                "nodes" to emptyList<Any>(),
                        ),
                )
                .exchange()
                .expectStatus().isNoContent

        assertThat(captured.captured.botId).isEqualTo("bot-1")
    }

    @Test
    fun `list returns owner-scoped summaries newest-first`() {
        every { userSessionProvider.getCurrentUserSessionOrFail() } returns session()
        every { executionService.list("bot-1", ownerId, 20, 0L) } returns
                Flux.just(summary("e2"), summary("e1"))

        client.get().uri("/api/bots/bot-1/executions")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo("e2")
                .jsonPath("$[0].nodeCount").isEqualTo(2)
    }

    @Test
    fun `list forwards paging query params`() {
        every { userSessionProvider.getCurrentUserSessionOrFail() } returns session()
        every { executionService.list("bot-1", ownerId, 5, 10L) } returns Flux.empty()

        client.get().uri("/api/bots/bot-1/executions?limit=5&offset=10")
                .exchange()
                .expectStatus().isOk
    }

    @Test
    fun `list of another users bot is rejected with 400`() {
        every { userSessionProvider.getCurrentUserSessionOrFail() } returns session()
        every { executionService.list("foreign", ownerId, 20, 0L) } returns
                Flux.error(InvalidRequestException("Bot", "not found"))

        client.get().uri("/api/bots/foreign/executions")
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.errors.Bot[0]").isEqualTo("not found")
    }

    @Test
    fun `get returns the full execution detail`() {
        val view = ExecutionView(
                id = "e1",
                botId = "bot-1",
                status = "success",
                trigger = "message",
                startedAt = Instant.parse("2026-06-16T10:00:00Z"),
                finishedAt = Instant.parse("2026-06-16T10:00:01Z"),
                message = "hi",
                reply = "hello",
                nodes = emptyList(),
        )
        every { userSessionProvider.getCurrentUserSessionOrFail() } returns session()
        every { executionService.get("e1", ownerId) } returns Mono.just(view)

        client.get().uri("/api/executions/e1")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo("e1")
    }

    @Test
    fun `get is 404-equivalent for a non-owner`() {
        every { userSessionProvider.getCurrentUserSessionOrFail() } returns session()
        every { executionService.get("e1", ownerId) } returns
                Mono.error(InvalidRequestException("Execution", "not found"))

        client.get().uri("/api/executions/e1")
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.errors.Execution[0]").isEqualTo("not found")
    }
}

package com.example.botconstructor.api

import com.example.botconstructor.config.WebConfig
import com.example.botconstructor.dto.BotRequest
import com.example.botconstructor.dto.BotView
import com.example.botconstructor.dto.EdgeView
import com.example.botconstructor.dto.NodeRequest
import com.example.botconstructor.dto.NodeView
import com.example.botconstructor.dto.PositionView
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.model.BotType
import com.example.botconstructor.model.User
import com.example.botconstructor.services.BotService
import com.example.botconstructor.services.UserSession
import com.example.botconstructor.services.UserSessionProvider
import com.example.botconstructor.validation.RequestValidator
import jakarta.validation.Validation
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class BotHandlerTest {

    private val botService = mockk<BotService>()
    private val userSessionProvider = mockk<UserSessionProvider>()
    private val userHandler = mockk<UserHandler>(relaxed = true)

    private val ownerId = "owner-1"
    private val requestValidator = RequestValidator(Validation.buildDefaultValidatorFactory().validator)
    private val botHandler = BotHandler(botService, userSessionProvider, requestValidator)

    private val executionHandler = mockk<ExecutionHandler>(relaxed = true)
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

    private fun sampleRequest() = BotRequest(
            name = "Support",
            type = BotType.Telegram,
            nodes = listOf(NodeRequest(id = "n1", type = "trigger")),
            edges = emptyList(),
            fallbackAnswer = "Sorry, I did not understand",
    )

    private fun sampleView(id: String) = BotView(
            id = id,
            name = "Support",
            type = "Telegram",
            ownerId = ownerId,
            nodes = listOf(NodeView("n1", "trigger", PositionView(0.0, 0.0), emptyMap())),
            edges = emptyList<EdgeView>(),
            fallbackAnswer = "Sorry, I did not understand",
            webhookToken = "tok-$id",
    )

    @Test
    fun `create returns the created bot`() {
        every { userSessionProvider.getCurrentUserSessionOrFail() } returns session()
        every { botService.create(any(), ownerId) } returns Mono.just(sampleView("bot-1"))

        client.post().uri("/api/bots")
                .bodyValue(sampleRequest())
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo("bot-1")
                .jsonPath("$.ownerId").isEqualTo(ownerId)
    }

    @Test
    fun `list returns the owners bots`() {
        every { userSessionProvider.getCurrentUserSessionOrFail() } returns session()
        every { botService.list(ownerId) } returns Flux.just(sampleView("a"), sampleView("b"))

        client.get().uri("/api/bots")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo("a")
    }

    @Test
    fun `internal webhook lookup returns the bot without a user session`() {
        every { botService.findByWebhookToken("secret-token") } returns Mono.just(sampleView("bot-1"))

        client.get().uri("/api/internal/bots/by-webhook/secret-token")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo("bot-1")
                .jsonPath("$.webhookToken").isEqualTo("tok-bot-1")
    }

    @Test
    fun `internal webhook lookup returns 404 for an unknown token`() {
        every { botService.findByWebhookToken("nope") } returns Mono.empty()

        client.get().uri("/api/internal/bots/by-webhook/nope")
                .exchange()
                .expectStatus().isNotFound
    }

    @Test
    fun `get another users bot is rejected with 400`() {
        every { userSessionProvider.getCurrentUserSessionOrFail() } returns session()
        every { botService.get("foreign", ownerId) } returns
                Mono.error(InvalidRequestException("Bot", "not found"))

        client.get().uri("/api/bots/foreign")
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.errors.Bot[0]").isEqualTo("not found")
    }
}

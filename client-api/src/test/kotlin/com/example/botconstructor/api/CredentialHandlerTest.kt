package com.example.botconstructor.api

import com.example.botconstructor.config.WebConfig
import com.example.botconstructor.dto.CredentialRequest
import com.example.botconstructor.dto.CredentialSecretView
import com.example.botconstructor.dto.CredentialView
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.model.User
import com.example.botconstructor.services.CredentialService
import com.example.botconstructor.services.UserSession
import com.example.botconstructor.services.UserSessionProvider
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant

class CredentialHandlerTest {

    private val credentialService = mockk<CredentialService>()
    private val userSessionProvider = mockk<UserSessionProvider>()
    private val userHandler = mockk<UserHandler>(relaxed = true)
    private val botHandler = mockk<BotHandler>(relaxed = true)
    private val executionHandler = mockk<ExecutionHandler>(relaxed = true)

    private val ownerId = "owner-1"
    private val credentialHandler = CredentialHandler(credentialService, userSessionProvider)

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

    private fun view(id: String) = CredentialView(
            id = id,
            name = "Prod bot",
            type = "telegramApi",
            createdAt = Instant.parse("2026-06-16T10:00:00Z"),
    )

    @Test
    fun `create returns metadata only and never the secret`() {
        every { userSessionProvider.getCurrentUserSessionOrFail() } returns session()
        every { credentialService.create(any(), ownerId) } returns Mono.just(view("c1"))

        client.post().uri("/api/credentials")
                .bodyValue(CredentialRequest(name = "Prod bot", type = "telegramApi", data = mapOf("botToken" to "123:secret")))
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.id").isEqualTo("c1")
                .jsonPath("$.type").isEqualTo("telegramApi")
                .jsonPath("$.data").doesNotExist()
                .jsonPath("$.botToken").doesNotExist()
                .jsonPath("$.encrypted").doesNotExist()
    }

    @Test
    fun `list returns metadata only and no secret fields`() {
        every { userSessionProvider.getCurrentUserSessionOrFail() } returns session()
        every { credentialService.list(ownerId) } returns Flux.just(view("a"), view("b"))

        client.get().uri("/api/credentials")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2)
                .jsonPath("$[0].id").isEqualTo("a")
                .jsonPath("$[0].data").doesNotExist()
                .jsonPath("$[0].encrypted").doesNotExist()
    }

    @Test
    fun `get a missing or foreign credential is rejected with 400 not-found`() {
        every { userSessionProvider.getCurrentUserSessionOrFail() } returns session()
        every { credentialService.get("foreign", ownerId) } returns
                Mono.error(InvalidRequestException("Credential", "not found"))

        client.get().uri("/api/credentials/foreign")
                .exchange()
                .expectStatus().isBadRequest
                .expectBody()
                .jsonPath("$.errors.Credential[0]").isEqualTo("not found")
    }

    @Test
    fun `delete returns 204`() {
        every { userSessionProvider.getCurrentUserSessionOrFail() } returns session()
        every { credentialService.delete("c1", ownerId) } returns Mono.empty()

        client.delete().uri("/api/credentials/c1")
                .exchange()
                .expectStatus().isNoContent
    }

    @Test
    fun `internal resolve returns the decrypted secret without a user session`() {
        every { credentialService.resolveForBot("c1", "bot-1") } returns
                Mono.just(CredentialSecretView("telegramApi", mapOf("botToken" to "123:secret")))

        client.get().uri("/api/internal/credentials/c1?botId=bot-1")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.type").isEqualTo("telegramApi")
                .jsonPath("$.data.botToken").isEqualTo("123:secret")
    }

    @Test
    fun `internal resolve is 404 when owners do not match`() {
        every { credentialService.resolveForBot("c1", "bot-1") } returns Mono.empty()

        client.get().uri("/api/internal/credentials/c1?botId=bot-1")
                .exchange()
                .expectStatus().isNotFound
    }

    @Test
    fun `internal resolve is 404 when botId is missing`() {
        client.get().uri("/api/internal/credentials/c1")
                .exchange()
                .expectStatus().isNotFound
    }
}

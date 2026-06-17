package com.example.botconstructor.services

import com.example.botconstructor.dto.CredentialRequest
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.model.BotTemplate
import com.example.botconstructor.model.BotType
import com.example.botconstructor.model.Credential
import com.example.botconstructor.repos.BotTemplateRepository
import com.example.botconstructor.repos.CredentialRepository
import com.example.botconstructor.security.CredentialCipher
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.util.Base64

class CredentialServiceTest {

    private val credentialRepository = mockk<CredentialRepository>()
    private val botTemplateRepository = mockk<BotTemplateRepository>()
    private val cipher = CredentialCipher(
            mapOf(CredentialCipher.KEY_ENV to Base64.getEncoder().encodeToString(ByteArray(32) { 7 })),
    )
    private val service = CredentialService(credentialRepository, botTemplateRepository, cipher)

    private val ownerId = "owner-1"

    private fun telegramRequest() = CredentialRequest(
            name = "Prod bot",
            type = "telegramApi",
            data = mapOf("botToken" to "123:secret"),
    )

    private fun bot(owner: String) = BotTemplate(
            id = "bot-1",
            name = "Support",
            type = BotType.Telegram,
            ownerId = owner,
            fallbackAnswer = "fallback",
    )

    @Test
    fun `create encrypts the secret and returns metadata only`() {
        val saved = slot<Credential>()
        every { credentialRepository.save(capture(saved)) } answers { Mono.just(saved.captured) }

        StepVerifier.create(service.create(telegramRequest(), ownerId))
                .assertNext { view ->
                    assertThat(view.name).isEqualTo("Prod bot")
                    assertThat(view.type).isEqualTo("telegramApi")
                    // Stored ciphertext is not the plaintext secret and decrypts back to it.
                    assertThat(saved.captured.encrypted).doesNotContain("123:secret")
                    assertThat(cipher.decrypt(saved.captured.encrypted)).contains("123:secret")
                    assertThat(saved.captured.ownerId).isEqualTo(ownerId)
                }
                .verifyComplete()
    }

    @Test
    fun `create rejects an unknown type`() {
        StepVerifier.create(service.create(telegramRequest().copy(type = "nope"), ownerId))
                .expectError(InvalidRequestException::class.java)
                .verify()
    }

    @Test
    fun `create rejects data with the wrong fields for the type`() {
        val bad = telegramRequest().copy(data = mapOf("apiKey" to "x"))

        StepVerifier.create(service.create(bad, ownerId))
                .expectError(InvalidRequestException::class.java)
                .verify()
    }

    @Test
    fun `create rejects extra fields beyond the type's shape`() {
        val bad = telegramRequest().copy(data = mapOf("botToken" to "x", "extra" to "y"))

        StepVerifier.create(service.create(bad, ownerId))
                .expectError(InvalidRequestException::class.java)
                .verify()
    }

    @Test
    fun `get is not-found for a missing or foreign credential`() {
        every { credentialRepository.findByIdAndOwnerId("c1", ownerId) } returns Mono.empty()

        StepVerifier.create(service.get("c1", ownerId))
                .expectError(InvalidRequestException::class.java)
                .verify()
    }

    @Test
    fun `list returns the owners credentials as metadata`() {
        val cred = Credential("c1", ownerId, "n", "telegramApi", cipher.encrypt("{}"))
        every { credentialRepository.findByOwnerId(ownerId) } returns Flux.just(cred)

        StepVerifier.create(service.list(ownerId).collectList())
                .assertNext { assertThat(it.single().id).isEqualTo("c1") }
                .verifyComplete()
    }

    @Test
    fun `resolveForBot returns the decrypted secret when owners match`() {
        val cred = Credential("c1", ownerId, "n", "telegramApi", cipher.encrypt("""{"botToken":"123:secret"}"""))
        every { botTemplateRepository.findById("bot-1") } returns Mono.just(bot(ownerId))
        every { credentialRepository.findById("c1") } returns Mono.just(cred)

        StepVerifier.create(service.resolveForBot("c1", "bot-1"))
                .assertNext {
                    assertThat(it.type).isEqualTo("telegramApi")
                    assertThat(it.data).isEqualTo(mapOf("botToken" to "123:secret"))
                }
                .verifyComplete()
    }

    @Test
    fun `resolveForBot is empty for a credential owned by a different user than the bot`() {
        val foreignCred = Credential("c1", "someone-else", "n", "telegramApi", cipher.encrypt("{}"))
        every { botTemplateRepository.findById("bot-1") } returns Mono.just(bot(ownerId))
        every { credentialRepository.findById("c1") } returns Mono.just(foreignCred)

        StepVerifier.create(service.resolveForBot("c1", "bot-1"))
                .verifyComplete()
    }

    @Test
    fun `resolveForBot is empty for an unknown bot`() {
        every { botTemplateRepository.findById("ghost") } returns Mono.empty()

        StepVerifier.create(service.resolveForBot("c1", "ghost"))
                .verifyComplete()
    }
}

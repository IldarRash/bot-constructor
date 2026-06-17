package com.example.botconstructor.services

import com.example.botconstructor.dto.CredentialRequest
import com.example.botconstructor.dto.CredentialSecretView
import com.example.botconstructor.dto.CredentialType
import com.example.botconstructor.dto.CredentialView
import com.example.botconstructor.dto.toView
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.model.Credential
import com.example.botconstructor.repos.BotTemplateRepository
import com.example.botconstructor.repos.CredentialRepository
import com.example.botconstructor.security.CredentialCipher
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Owner-scoped CRUD for encrypted credentials plus the internal decrypted fetch. Secrets are
 * encrypted on write and only ever decrypted inside [resolveForBot]; owner-facing methods return
 * metadata-only [CredentialView]s. Not-found semantics mirror [BotService]: a missing or foreign
 * credential returns the same opaque `Credential not found` (no IDOR leak).
 *
 * @property credentialRepository Reactive store of encrypted credential documents.
 * @property botTemplateRepository Resolves a bot's owner for the anti-IDOR boundary in [resolveForBot].
 * @property cipher AES-256-GCM cipher; the plaintext secret never leaves this service except through
 * [resolveForBot].
 */
@Component
class CredentialService(
        private val credentialRepository: CredentialRepository,
        private val botTemplateRepository: BotTemplateRepository,
        private val cipher: CredentialCipher,
) {

    private val mapper: ObjectMapper = jacksonObjectMapper()

    /**
     * Creates a credential owned by [ownerId]. The body must declare a known [CredentialRequest.type]
     * and a [CredentialRequest.data] map carrying exactly that type's fields; the data is encrypted
     * before storage.
     */
    fun create(request: CredentialRequest, ownerId: String): Mono<CredentialView> {
        val name = request.name?.takeIf { it.isNotBlank() }
                ?: return Mono.error(InvalidRequestException("Credential", "name is required"))
        val type = CredentialType.fromId(request.type)
                ?: return Mono.error(InvalidRequestException("Credential", "unknown type"))
        val data = request.data
                ?: return Mono.error(InvalidRequestException("Credential", "data is required"))
        return validateData(type, data)
                .flatMap {
                    val credential = Credential(
                            id = UUID.randomUUID().toString(),
                            ownerId = ownerId,
                            name = name,
                            type = type.id,
                            encrypted = cipher.encrypt(mapper.writeValueAsString(data)),
                            createdAt = Instant.now(),
                    )
                    credentialRepository.save(credential)
                }
                .map { it.toView() }
    }

    /** Lists every credential owned by [ownerId] as metadata-only views. */
    fun list(ownerId: String): Flux<CredentialView> =
            credentialRepository.findByOwnerId(ownerId).map { it.toView() }

    /** Gets one owned credential as a metadata-only view, or the opaque not-found error. */
    fun get(id: String, ownerId: String): Mono<CredentialView> =
            findOwnedOrFail(id, ownerId).map { it.toView() }

    /**
     * Updates the owned credential [id]: a present, non-blank [CredentialRequest.name] renames it; a
     * present [CredentialRequest.data] re-encrypts against the stored type. The type is immutable, so
     * supplied data is validated against the credential's existing type.
     */
    fun update(id: String, request: CredentialRequest, ownerId: String): Mono<CredentialView> {
        return findOwnedOrFail(id, ownerId)
                .flatMap { existing ->
                    val type = CredentialType.fromId(existing.type)
                            ?: return@flatMap Mono.error<Credential>(
                                    InvalidRequestException("Credential", "unknown type"))
                    val renamed = request.name?.takeIf { it.isNotBlank() }
                            ?.let { existing.copy(name = it) } ?: existing
                    val data = request.data
                            ?: return@flatMap credentialRepository.save(renamed)
                    validateData(type, data).map {
                        renamed.copy(encrypted = cipher.encrypt(mapper.writeValueAsString(data)))
                    }.flatMap { credentialRepository.save(it) }
                }
                .map { it.toView() }
    }

    /** Deletes the owned credential [id]; a missing/foreign id yields the opaque not-found error. */
    fun delete(id: String, ownerId: String): Mono<Void> =
            findOwnedOrFail(id, ownerId).flatMap { credentialRepository.delete(it) }

    /**
     * The anti-IDOR internal fetch: loads the bot by [botId] to learn its owner, loads the credential
     * by [credId], and returns the DECRYPTED secret ONLY when both belong to the same owner. Any
     * mismatch — unknown bot, unknown credential, or a credential owned by a different user than the
     * bot — completes empty so the handler returns an opaque 404. This is what stops a bot owner from
     * resolving another user's credential by referencing its id in their graph.
     */
    fun resolveForBot(credId: String, botId: String): Mono<CredentialSecretView> {
        return botTemplateRepository.findById(botId)
                .flatMap { bot ->
                    credentialRepository.findById(credId)
                            .filter { it.ownerId == bot.ownerId }
                }
                .map { credential ->
                    val data: Map<String, String> = mapper.readValue(cipher.decrypt(credential.encrypted))
                    CredentialSecretView(type = credential.type, data = data)
                }
    }

    /**
     * Rejects a [data] map whose keys are not EXACTLY the [type]'s fields, or any field that is blank.
     * Deferred into the reactive chain so callers stay non-blocking.
     */
    private fun validateData(type: CredentialType, data: Map<String, String>): Mono<Unit> {
        val error = when {
            data.keys != type.fields.toSet() ->
                "data must have exactly the fields ${type.fields} for type ${type.id}"
            data.values.any { it.isBlank() } -> "credential fields must not be blank"
            else -> null
        }
        return error?.let { Mono.error(InvalidRequestException("Credential", it)) } ?: Mono.just(Unit)
    }

    private fun findOwnedOrFail(id: String, ownerId: String): Mono<Credential> =
            credentialRepository.findByIdAndOwnerId(id, ownerId)
                    .switchIfEmpty(Mono.error(InvalidRequestException("Credential", "not found")))
}

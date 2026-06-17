package com.example.botconstructor.api

import com.example.botconstructor.dto.CredentialRequest
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.services.CredentialService
import com.example.botconstructor.services.UserSessionProvider
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.badRequest
import org.springframework.web.reactive.function.server.ServerResponse.noContent
import org.springframework.web.reactive.function.server.ServerResponse.notFound
import org.springframework.web.reactive.function.server.ServerResponse.ok
import reactor.core.publisher.Mono

/**
 * Handles credential HTTP requests. The five CRUD routes are JWT-authenticated and owner-scoped and
 * return metadata-only views (never secrets). The internal fetch is the only place a decrypted secret
 * leaves client-api, and only server-to-server (the gateway 404s the internal namespace).
 *
 * @property credentialService Owner-scoped CRUD plus the anti-IDOR internal resolve.
 * @property userSessionProvider Resolves the verified current user for the CRUD routes.
 */
@Service
class CredentialHandler(
        private val credentialService: CredentialService,
        private val userSessionProvider: UserSessionProvider,
) {

    /** Creates an encrypted credential owned by the current user; returns metadata only. */
    fun createCredential(serverRequest: ServerRequest): Mono<ServerResponse> {
        return userSessionProvider.getCurrentUserSessionOrFail()
                .zipWith(serverRequest.bodyToMono(CredentialRequest::class.java))
                .flatMap { credentialService.create(it.t2, it.t1.user.id) }
                .flatMap { ok().bodyValue(it) }
                .onErrorResume(::handleInvalidRequestException)
    }

    /** Lists the current user's credentials as metadata-only views. */
    fun listCredentials(serverRequest: ServerRequest): Mono<ServerResponse> {
        return userSessionProvider.getCurrentUserSessionOrFail()
                .flatMap { session ->
                    credentialService.list(session.user.id)
                            .collectList()
                            .flatMap { ok().bodyValue(it) }
                }
                .onErrorResume(::handleInvalidRequestException)
    }

    /** Gets one owned credential as a metadata-only view. */
    fun getCredential(serverRequest: ServerRequest): Mono<ServerResponse> {
        val id = serverRequest.pathVariable("id")
        return userSessionProvider.getCurrentUserSessionOrFail()
                .flatMap { session -> credentialService.get(id, session.user.id) }
                .flatMap { ok().bodyValue(it) }
                .onErrorResume(::handleInvalidRequestException)
    }

    /** Updates an owned credential (rename and/or re-encrypt); returns metadata only. */
    fun updateCredential(serverRequest: ServerRequest): Mono<ServerResponse> {
        val id = serverRequest.pathVariable("id")
        return userSessionProvider.getCurrentUserSessionOrFail()
                .zipWith(serverRequest.bodyToMono(CredentialRequest::class.java))
                .flatMap { credentialService.update(id, it.t2, it.t1.user.id) }
                .flatMap { ok().bodyValue(it) }
                .onErrorResume(::handleInvalidRequestException)
    }

    /** Deletes an owned credential. */
    fun deleteCredential(serverRequest: ServerRequest): Mono<ServerResponse> {
        val id = serverRequest.pathVariable("id")
        return userSessionProvider.getCurrentUserSessionOrFail()
                .flatMap { session -> credentialService.delete(id, session.user.id) }
                .then(noContent().build())
                .onErrorResume(::handleInvalidRequestException)
    }

    /**
     * Internal, unauthenticated server-to-server fetch of a DECRYPTED credential, called by bot-api
     * during a flow run. The credential id is in the path and the bot id is a query param; the service
     * returns the secret only when the credential and bot share an owner, else completes empty and we
     * return an opaque 404 (the anti-IDOR boundary). There is no user session here; the route is
     * permitted in SecurityConfig under the internal subpath and is unreachable through the gateway.
     */
    fun resolveCredential(serverRequest: ServerRequest): Mono<ServerResponse> {
        val credId = serverRequest.pathVariable("credId")
        val botId = serverRequest.queryParam("botId").orElse("")
        if (botId.isBlank()) {
            return notFound().build()
        }
        return credentialService.resolveForBot(credId, botId)
                .flatMap { ok().bodyValue(it) }
                .switchIfEmpty(notFound().build())
                .onErrorResume(::handleInvalidRequestException)
    }

    private fun handleInvalidRequestException(exception: Throwable): Mono<ServerResponse> {
        return if (exception is InvalidRequestException) {
            val response = InvalidRequestExceptionResponse(mapOf(exception.subject to listOf(exception.violation)))
            badRequest().bodyValue(response)
        } else {
            Mono.error(exception)
        }
    }

    private data class InvalidRequestExceptionResponse(val errors: Map<String, List<String>>)
}

package com.example.botconstructor.api

import com.example.botconstructor.dto.ExecutionRecordRequest
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.services.ExecutionService
import com.example.botconstructor.services.UserSessionProvider
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.badRequest
import org.springframework.web.reactive.function.server.ServerResponse.noContent
import org.springframework.web.reactive.function.server.ServerResponse.ok
import reactor.core.publisher.Mono

/**
 * Handles execution-record HTTP requests. The internal write route is server-to-server only (the
 * gateway 404s every internal path); the two read routes are authenticated and owner-scoped.
 *
 * @property executionService Owner-scoped persistence and reads for execution records.
 * @property userSessionProvider Resolves the verified current user for the read routes.
 */
@Service
class ExecutionHandler(
        private val executionService: ExecutionService,
        private val userSessionProvider: UserSessionProvider,
) {

    private val defaultLimit = 20
    private val maxLimit = 100

    /**
     * Internal, unauthenticated server-to-server persist of an execution record posted by bot-api.
     * The owner is resolved from the bot document inside the service (never trusted from the body),
     * and a record for an already-deleted bot is dropped without error. There is no user session
     * here; the route is permitted in SecurityConfig under the internal subpath and is unreachable
     * through the gateway.
     */
    fun recordExecution(serverRequest: ServerRequest): Mono<ServerResponse> {
        return serverRequest.bodyToMono(ExecutionRecordRequest::class.java)
                .flatMap { executionService.persist(it) }
                .then(noContent().build())
                .onErrorResume(::handleInvalidRequestException)
    }

    /**
     * Lists the current user's executions for one owned bot, newest-first and paged.
     */
    fun listExecutions(serverRequest: ServerRequest): Mono<ServerResponse> {
        val botId = serverRequest.pathVariable("id")
        val limit = serverRequest.queryParam("limit")
                .map { it.toIntOrNull() ?: defaultLimit }
                .orElse(defaultLimit)
                .coerceIn(1, maxLimit)
        val offset = serverRequest.queryParam("offset")
                .map { it.toLongOrNull() ?: 0L }
                .orElse(0L)
                .coerceAtLeast(0L)
        return userSessionProvider.getCurrentUserSessionOrFail()
                .flatMap { session ->
                    executionService.list(botId, session.user.id, limit, offset)
                            .collectList()
                            .flatMap { ok().bodyValue(it) }
                }
                .onErrorResume(::handleInvalidRequestException)
    }

    /**
     * Returns the full execution detail for the current user, owner-scoped.
     */
    fun getExecution(serverRequest: ServerRequest): Mono<ServerResponse> {
        val execId = serverRequest.pathVariable("execId")
        return userSessionProvider.getCurrentUserSessionOrFail()
                .flatMap { session -> executionService.get(execId, session.user.id) }
                .flatMap { ok().bodyValue(it) }
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

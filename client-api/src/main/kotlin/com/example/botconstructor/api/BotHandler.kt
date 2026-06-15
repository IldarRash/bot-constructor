package com.example.botconstructor.api

import com.example.botconstructor.dto.BotRequest
import com.example.botconstructor.dto.BotView
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.services.BotService
import com.example.botconstructor.services.UserSessionProvider
import com.example.botconstructor.validation.RequestValidator
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.badRequest
import org.springframework.web.reactive.function.server.ServerResponse.noContent
import org.springframework.web.reactive.function.server.ServerResponse.ok
import reactor.core.publisher.Mono

/**
 * Handles bot-related HTTP requests. All routes are authenticated and owner-scoped.
 *
 * @property botService The service for owner-scoped bot operations.
 * @property userSessionProvider The provider for resolving the current user session.
 */
@Service
class BotHandler(
        private val botService: BotService,
        private val userSessionProvider: UserSessionProvider,
        private val requestValidator: RequestValidator,
) {

    /**
     * Creates a bot owned by the current user.
     */
    fun createBot(serverRequest: ServerRequest): Mono<ServerResponse> {
        return userSessionProvider.getCurrentUserSessionOrFail()
                .zipWith(serverRequest.bodyToMono(BotRequest::class.java))
                .flatMap { botService.create(requestValidator.validate(it.t2), it.t1.user.id) }
                .flatMap { ok().bodyValue(it) }
                .onErrorResume(::handleInvalidRequestException)
    }

    /**
     * Lists all bots owned by the current user.
     */
    fun listBots(serverRequest: ServerRequest): Mono<ServerResponse> {
        return userSessionProvider.getCurrentUserSessionOrFail()
                .flatMap { session ->
                    botService.list(session.user.id)
                            .collectList()
                            .flatMap { ok().bodyValue(it) }
                }
                .onErrorResume(::handleInvalidRequestException)
    }

    /**
     * Gets a single bot owned by the current user.
     */
    fun getBot(serverRequest: ServerRequest): Mono<ServerResponse> {
        val id = serverRequest.pathVariable("id")
        return userSessionProvider.getCurrentUserSessionOrFail()
                .flatMap { session -> botService.get(id, session.user.id) }
                .flatMap { ok().bodyValue(it) }
                .onErrorResume(::handleInvalidRequestException)
    }

    /**
     * Updates a bot owned by the current user.
     */
    fun updateBot(serverRequest: ServerRequest): Mono<ServerResponse> {
        val id = serverRequest.pathVariable("id")
        return userSessionProvider.getCurrentUserSessionOrFail()
                .zipWith(serverRequest.bodyToMono(BotRequest::class.java))
                .flatMap { botService.update(id, requestValidator.validate(it.t2), it.t1.user.id) }
                .flatMap { ok().bodyValue(it) }
                .onErrorResume(::handleInvalidRequestException)
    }

    /**
     * Deletes a bot owned by the current user.
     */
    fun deleteBot(serverRequest: ServerRequest): Mono<ServerResponse> {
        val id = serverRequest.pathVariable("id")
        return userSessionProvider.getCurrentUserSessionOrFail()
                .flatMap { session -> botService.delete(id, session.user.id) }
                .then(noContent().build())
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

package com.example.botconstructor.botapi.api

import com.example.botconstructor.botapi.model.dto.MessageRequest
import com.example.botconstructor.botapi.runtime.RuntimeService
import com.example.botconstructor.botapi.runtime.SessionNotFoundException
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.notFound
import org.springframework.web.reactive.function.server.ServerResponse.ok
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Mono

/**
 * Handles bot runtime HTTP requests: starting sessions and exchanging messages.
 *
 * @property runtimeService The in-memory session runtime.
 */
@Service
class RuntimeHandler(
        private val runtimeService: RuntimeService,
) {

    /**
     * Starts a runtime session for the bot identified by the path variable `id`.
     */
    fun startSession(serverRequest: ServerRequest): Mono<ServerResponse> {
        val botId = serverRequest.pathVariable("id")
        val authHeader = serverRequest.headers().firstHeader(HttpHeaders.AUTHORIZATION)
        return runtimeService.startSession(botId, authHeader)
                .flatMap { ok().bodyValue(it) }
                .onErrorResume(::handleError)
    }

    /**
     * Handles a message sent to an existing session.
     */
    fun handleMessage(serverRequest: ServerRequest): Mono<ServerResponse> {
        val sessionId = serverRequest.pathVariable("sessionId")
        return serverRequest.bodyToMono(MessageRequest::class.java)
                .flatMap { runtimeService.handleMessage(sessionId, it.text) }
                .flatMap { ok().bodyValue(it) }
                .onErrorResume(::handleError)
    }

    private fun handleError(exception: Throwable): Mono<ServerResponse> {
        return when (exception) {
            is SessionNotFoundException -> notFound().build()
            // Propagate client-api's status (e.g. 401 unauthenticated, 400 bot not found/not owned)
            // instead of surfacing it as an opaque 500.
            is WebClientResponseException -> ServerResponse.status(exception.statusCode)
                    .bodyValue(mapOf("error" to "upstream client-api responded ${exception.statusCode}"))
            else -> Mono.error(exception)
        }
    }
}

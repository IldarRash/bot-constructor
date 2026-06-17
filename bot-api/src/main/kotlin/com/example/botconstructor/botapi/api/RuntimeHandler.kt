package com.example.botconstructor.botapi.api

import com.example.botconstructor.botapi.model.dto.ManualRunRequest
import com.example.botconstructor.botapi.model.dto.MessageRequest
import com.example.botconstructor.botapi.model.dto.WebhookRequest
import com.example.botconstructor.botapi.runtime.RuntimeService
import com.example.botconstructor.botapi.runtime.SessionNotFoundException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
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

    private companion object {
        /** Upper bound on a single user message, guarding the engine from oversized input. */
        const val MAX_MESSAGE_LENGTH = 4000
    }

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
     * Runs the saved flow for the bot `id` once, on demand, for the editor. Owner-scoped: the caller's
     * Authorization header is forwarded to client-api (mirroring [startSession]), so a non-owner gets
     * the same upstream not-found the session path gives. Returns the rich per-node data for inline
     * inspection and supports pinned data (skip a node, reuse a pinned output).
     */
    fun runManual(serverRequest: ServerRequest): Mono<ServerResponse> {
        val botId = serverRequest.pathVariable("id")
        val authHeader = serverRequest.headers().firstHeader(HttpHeaders.AUTHORIZATION)
        return serverRequest.bodyToMono(ManualRunRequest::class.java)
                // Tolerate an empty/absent body: run the saved flow with no message, vars, or pins.
                .defaultIfEmpty(ManualRunRequest())
                .flatMap { request ->
                    val message = request.message
                    if (message != null && message.length > MAX_MESSAGE_LENGTH) {
                        ServerResponse.badRequest()
                                .bodyValue(mapOf("error" to "message exceeds $MAX_MESSAGE_LENGTH characters"))
                    } else {
                        runtimeService.runManual(botId, authHeader, request)
                                .flatMap { ok().bodyValue(it) }
                    }
                }
                .onErrorResume(::handleError)
    }

    /**
     * Handles a message sent to an existing session.
     */
    fun handleMessage(serverRequest: ServerRequest): Mono<ServerResponse> {
        val sessionId = serverRequest.pathVariable("sessionId")
        return serverRequest.bodyToMono(MessageRequest::class.java)
                .flatMap { request ->
                    if (request.text.length > MAX_MESSAGE_LENGTH) {
                        ServerResponse.badRequest()
                                .bodyValue(mapOf("error" to "message exceeds $MAX_MESSAGE_LENGTH characters"))
                    } else {
                        runtimeService.handleMessage(sessionId, request.text)
                                .flatMap { ok().bodyValue(it) }
                    }
                }
                .onErrorResume(::handleError)
    }

    /**
     * Handles a stateless webhook invocation of the bot bound to the path variable `token`. Public
     * route: no auth handling — the token is the credential, resolved at the data layer. An unknown
     * token (client-api 404) maps to a 404 here.
     */
    fun runWebhook(serverRequest: ServerRequest): Mono<ServerResponse> {
        val token = serverRequest.pathVariable("token")
        return serverRequest.bodyToMono(WebhookRequest::class.java)
                // Tolerate an empty/absent body: run the flow with no message and no extra vars.
                .defaultIfEmpty(WebhookRequest())
                .flatMap { request ->
                    val message = request.message
                    if (message != null && message.length > MAX_MESSAGE_LENGTH) {
                        ServerResponse.badRequest()
                                .bodyValue(mapOf("error" to "message exceeds $MAX_MESSAGE_LENGTH characters"))
                    } else {
                        runtimeService.runWebhook(token, request)
                                .flatMap { ok().bodyValue(it) }
                    }
                }
                .onErrorResume(::handleWebhookError)
    }

    /** Maps an unknown webhook token (upstream client-api 404) to a 404, else reuses [handleError]. */
    private fun handleWebhookError(exception: Throwable): Mono<ServerResponse> {
        if (exception is WebClientResponseException && exception.statusCode == HttpStatus.NOT_FOUND) {
            return notFound().build()
        }
        return handleError(exception)
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

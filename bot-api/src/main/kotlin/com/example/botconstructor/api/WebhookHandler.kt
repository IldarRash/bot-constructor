package com.example.botconstructor.api

import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

interface WebhookHandler {

    fun webHook(request: ServerRequest): Mono<ServerResponse>

    fun subscribe(request: ServerRequest): Mono<ServerResponse>
}



@Service
class InstagramWebhookHandler : WebhookHandler {
    override fun webHook(request: ServerRequest): Mono<ServerResponse> {
        TODO("Not yet implemented")
    }

    override fun subscribe(request: ServerRequest): Mono<ServerResponse> {
        TODO("Not yet implemented")
    }
}

@Service
class TelegramWebhookHandler : WebhookHandler {

    override fun webHook(request: ServerRequest): Mono<ServerResponse> {
        TODO("Not yet implemented")
    }

    override fun subscribe(request: ServerRequest): Mono<ServerResponse> {
        TODO("Not yet implemented")
    }
}

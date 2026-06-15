package com.example.botconstructor.botapi.client

import com.example.botconstructor.botapi.model.dto.BotSummary
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

/**
 * Reactive client for client-api. Used to load a bot definition, forwarding the caller's
 * Authorization header so that client-api can enforce ownership.
 *
 * @property webClient The WebClient bound to the client-api base URL.
 */
@Component
class ClientApiClient(
        @param:Value("\${CLIENT_API_URI:http://localhost:9000}") clientApiUri: String,
) {

    private val webClient: WebClient = WebClient.builder()
            .baseUrl(clientApiUri)
            .build()

    /**
     * Fetches the bot [id] from client-api, forwarding [authHeader] as the Authorization header.
     */
    fun fetchBot(id: String, authHeader: String?): Mono<BotSummary> {
        return webClient.get()
                .uri("/api/bots/{id}", id)
                .headers { headers -> authHeader?.let { headers.set(HttpHeaders.AUTHORIZATION, it) } }
                .retrieve()
                .bodyToMono(BotSummary::class.java)
    }
}

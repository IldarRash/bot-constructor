package com.example.botconstructor.botapi.client

import com.example.botconstructor.botapi.engine.CredentialSecret
import com.example.botconstructor.botapi.model.dto.BotSummary
import com.example.botconstructor.botapi.model.dto.CredentialSecretView
import com.example.botconstructor.botapi.model.dto.ExecutionRecordRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
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

    /**
     * Resolves the bot bound to webhook [token] via client-api's internal permitAll lookup
     * (`GET /api/internal/bots/by-webhook/{token}`). No Authorization header is sent — the token is
     * the credential. An unknown token surfaces as a `WebClientResponseException` 404, which the
     * handler maps to a 404 response.
     */
    fun fetchBotByWebhook(token: String): Mono<BotSummary> {
        return webClient.get()
                .uri("/api/internal/bots/by-webhook/{token}", token)
                .retrieve()
                .bodyToMono(BotSummary::class.java)
    }

    /**
     * Resolves credential [credId] for the current bot [botId] via client-api's internal,
     * owner-scoped decrypt endpoint (`GET /api/internal/credentials/{id}?botId=...`). The endpoint
     * only returns the secret when the credential's owner matches the bot's owner — the anti-IDOR
     * boundary — and 404s (opaquely) otherwise. No Authorization header is sent: the internal
     * namespace is unreachable through the gateway, so this is server-to-server inside the cluster.
     *
     * Returns the decrypted [CredentialSecret], or an empty [Mono] on any 404 (unknown id, foreign
     * credential), so an unresolvable reference is indistinguishable from a missing one to the engine.
     * This goes through this client (NOT the engine's SSRF-filtered httpCaller) because client-api may
     * be a private host the SSRF filter would block.
     */
    fun fetchCredential(credId: String, botId: String): Mono<CredentialSecret> {
        return webClient.get()
                .uri { uriBuilder ->
                    uriBuilder.path("/api/internal/credentials/{id}").queryParam("botId", botId).build(credId)
                }
                .retrieve()
                .bodyToMono(CredentialSecretView::class.java)
                .map { view -> CredentialSecret(view.type, view.data) }
                .onErrorResume(WebClientResponseException::class.java) { ex ->
                    // A 404 is the opaque anti-IDOR signal: treat as "unresolvable", not an error.
                    if (ex.statusCode == HttpStatus.NOT_FOUND) Mono.empty() else Mono.error(ex)
                }
    }

    /**
     * Posts an execution [record] to client-api's internal permitAll sink
     * (`POST /api/internal/executions`). Server-to-server: no Authorization header is sent (the
     * gateway 404s every internal path, so this is reachable only inside the cluster). client-api
     * resolves the owning user from the bot document. Completes empty on success.
     */
    fun postExecution(record: ExecutionRecordRequest): Mono<Void> {
        return webClient.post()
                .uri("/api/internal/executions")
                .bodyValue(record)
                .retrieve()
                .bodyToMono(Void::class.java)
    }
}

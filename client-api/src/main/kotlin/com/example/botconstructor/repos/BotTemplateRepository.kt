package com.example.botconstructor.repos

import com.example.botconstructor.model.BotTemplate
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface BotTemplateRepository : ReactiveMongoRepository<BotTemplate, String> {
    fun findByOwnerId(ownerId: String): Flux<BotTemplate>

    /**
     * Resolves a bot by its exact webhook token. The token is the secret that authorizes the
     * unauthenticated server-to-server webhook lookup (no ownership check), so this is an exact
     * equality match — an unknown token yields an empty result, indistinguishable from any other
     * miss (no enumeration signal).
     */
    fun findByWebhookToken(webhookToken: String): Mono<BotTemplate>

    /**
     * Streams every bot that carries a (non-null) cron schedule. Used by `ScheduledFlowRunner` on
     * each scheduler tick to find candidate bots to evaluate for "due this minute". Bots without a
     * schedule are never returned. A persisted blank value is filtered out by the runner's due check.
     */
    fun findByScheduleNotNull(): Flux<BotTemplate>
}

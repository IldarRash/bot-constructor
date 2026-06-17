package com.example.botconstructor.repos

import com.example.botconstructor.model.Execution
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface ExecutionRepository : ReactiveMongoRepository<Execution, String> {

    /**
     * Resolves a single execution by id, scoped to its owner. A non-owner (or unknown id) yields an
     * empty result, indistinguishable from a true miss, so reads stay owner-scoped with no IDOR leak.
     */
    fun findByIdAndOwnerId(id: String, ownerId: String): Mono<Execution>

    /**
     * Page of one bot's executions, scoped to both the bot and its owner. The owner is matched as well
     * as the bot id so a forged bot id can never surface another user's runs. Ordering and paging are
     * supplied entirely via [Pageable] (use OffsetBasedPageable), which carries a total sort —
     * `startedAt` desc plus `id` desc as a unique tiebreaker — so offset paging is stable across fetches.
     */
    fun findByBotIdAndOwnerId(
            botId: String,
            ownerId: String,
            pageable: Pageable,
    ): Flux<Execution>

    /**
     * Total count of one bot's executions for that owner, for paging metadata.
     */
    fun countByBotIdAndOwnerId(botId: String, ownerId: String): Mono<Long>
}

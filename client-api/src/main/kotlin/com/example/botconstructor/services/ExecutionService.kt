package com.example.botconstructor.services

import com.example.botconstructor.dto.ExecutionNodeRecord
import com.example.botconstructor.dto.ExecutionRecordRequest
import com.example.botconstructor.dto.ExecutionSummaryView
import com.example.botconstructor.dto.ExecutionView
import com.example.botconstructor.dto.toSummaryView
import com.example.botconstructor.dto.toView
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.lib.OffsetBasedPageable
import com.example.botconstructor.model.Execution
import com.example.botconstructor.model.ExecutionNode
import com.example.botconstructor.repos.BotTemplateRepository
import com.example.botconstructor.repos.ExecutionRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Persists and serves bot flow execution records. Writes come from bot-api over the internal
 * endpoint and are owner-resolved here from the bot document; reads are strictly owner-scoped using
 * the same not-found semantics as [BotService] (no IDOR leak between missing and not-owned).
 *
 * @property executionRepository Reactive store of execution documents.
 * @property botTemplateRepository Used to resolve the owner of a run and to verify bot ownership.
 */
@Component
class ExecutionService(
        private val executionRepository: ExecutionRepository,
        private val botTemplateRepository: BotTemplateRepository,
) {

    /**
     * Persists an execution record sent by bot-api. The owner is resolved from the bot document, so
     * the runtime can never attribute a run to an arbitrary user. If the bot has since been deleted
     * the record is silently dropped (the chain completes empty) rather than failing the runtime —
     * an orphaned run is not worth a 500 on a server-to-server call.
     */
    fun persist(request: ExecutionRecordRequest): Mono<Execution> {
        return botTemplateRepository.findById(request.botId)
                .flatMap { bot ->
                    val execution = Execution(
                            id = UUID.randomUUID().toString(),
                            botId = request.botId,
                            ownerId = bot.ownerId,
                            status = request.status,
                            trigger = request.trigger,
                            startedAt = request.startedAt,
                            finishedAt = request.finishedAt,
                            message = request.message,
                            reply = request.reply,
                            nodes = request.nodes.map { it.toNode() },
                            createdAt = Instant.now(),
                    )
                    executionRepository.save(execution)
                }
    }

    /**
     * Newest-first page of summaries for one bot's runs, owner-scoped. The bot's ownership is
     * verified first so a forged/foreign bot id yields the same `Bot not found` as [BotService]
     * before any execution is read.
     */
    fun list(botId: String, ownerId: String, limit: Int, offset: Long): Flux<ExecutionSummaryView> {
        // `startedAt` alone is non-unique (a webhook/schedule burst can share an instant); add `id` as a
        // total tiebreaker so offset paging is stable across page fetches (no skip/duplicate at a boundary).
        val sort = Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("id"))
        val pageable = OffsetBasedPageable(limit, offset, sort)
        return verifyBotOwned(botId, ownerId)
                .flatMapMany {
                    executionRepository.findByBotIdAndOwnerId(botId, ownerId, pageable)
                }
                .map { it.toSummaryView() }
    }

    /**
     * Full execution detail, owner-scoped. Returns the same opaque `Execution not found` whether the
     * id is unknown or simply owned by someone else.
     */
    fun get(execId: String, ownerId: String): Mono<ExecutionView> {
        return executionRepository.findByIdAndOwnerId(execId, ownerId)
                .switchIfEmpty(Mono.error(InvalidRequestException("Execution", "not found")))
                .map { it.toView() }
    }

    private fun verifyBotOwned(botId: String, ownerId: String): Mono<Boolean> {
        return botTemplateRepository.findById(botId)
                .switchIfEmpty(Mono.error(InvalidRequestException("Bot", "not found")))
                .flatMap { bot ->
                    if (bot.ownerId != ownerId) {
                        Mono.error(InvalidRequestException("Bot", "not found"))
                    } else {
                        Mono.just(true)
                    }
                }
    }

    private fun ExecutionNodeRecord.toNode() = ExecutionNode(
            nodeId = nodeId,
            type = type,
            handle = handle,
            detail = detail,
            inputItems = inputItems,
            outputs = outputs,
            error = error,
    )
}

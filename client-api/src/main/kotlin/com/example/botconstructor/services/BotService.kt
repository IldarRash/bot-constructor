package com.example.botconstructor.services

import com.example.botconstructor.dto.BotRequest
import com.example.botconstructor.dto.BotView
import com.example.botconstructor.dto.toView
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.model.BotTemplate
import com.example.botconstructor.repos.BotTemplateRepository
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Owner-scoped CRUD for bots. Every operation is bound to the supplied owner id so a user
 * can never read or mutate another user's bot.
 *
 * @property botTemplateRepository The reactive repository for bot documents.
 */
@Component
class BotService(
        private val botTemplateRepository: BotTemplateRepository,
) {

    /**
     * Creates a new bot owned by [ownerId].
     */
    fun create(request: BotRequest, ownerId: String): Mono<BotView> {
        return validateGraph(request)
                .flatMap { botTemplateRepository.save(it.toBotTemplate(UUID.randomUUID().toString(), ownerId)) }
                .map { it.toView() }
    }

    /**
     * Lists all bots owned by [ownerId].
     */
    fun list(ownerId: String): Flux<BotView> {
        // Strip the webhook secret from the list response: a single bulk call would otherwise hand
        // out every bot's live execution token. The owner reads it from the single-bot GET instead.
        return botTemplateRepository.findByOwnerId(ownerId)
                .map { it.toView().copy(webhookToken = null) }
    }

    /**
     * Gets a single bot by [id], verifying it belongs to [ownerId].
     */
    fun get(id: String, ownerId: String): Mono<BotView> {
        return findOwnedOrFail(id, ownerId)
                .map { it.toView() }
    }

    /**
     * Updates the bot [id] owned by [ownerId] from [request]. The existing bot is normalized first
     * so a legacy/token-less document gains a [BotTemplate.webhookToken] that the persisted update
     * preserves; the request can never set or change the token (it has no token field).
     */
    fun update(id: String, request: BotRequest, ownerId: String): Mono<BotView> {
        return validateGraph(request)
                .flatMap { findOwnedOrFail(id, ownerId) }
                .map { request.applyTo(it.normalized()) }
                .flatMap { botTemplateRepository.save(it) }
                .map { it.toView() }
    }

    /**
     * Resolves a bot by its webhook [token] for the unauthenticated internal lookup that bot-api
     * calls server-to-server on behalf of a webhook caller. The token itself is the authorization
     * (knowing it authorizes running/reading exactly this bot), so there is no ownership check. An
     * unknown token completes empty so the caller returns the same opaque 404 (no enumeration).
     * A blank token is rejected up front so it can never match a legacy null/blank-token document.
     */
    fun findByWebhookToken(token: String): Mono<BotView> {
        if (token.isBlank()) {
            return Mono.empty()
        }
        return botTemplateRepository.findByWebhookToken(token)
                .map { it.toView() }
    }

    /**
     * Deletes the bot [id] owned by [ownerId].
     */
    fun delete(id: String, ownerId: String): Mono<Void> {
        return findOwnedOrFail(id, ownerId)
                .flatMap { botTemplateRepository.delete(it) }
    }

    /**
     * Rejects structurally invalid graphs (no single trigger, duplicate node/edge ids, or edges
     * pointing at nonexistent nodes) and invalid cron schedules, deferring the failure into the
     * reactive chain so callers stay non-blocking. Size limits are enforced upstream by bean
     * validation on [BotRequest]. A null/blank [BotRequest.schedule] means "not scheduled" and is
     * always allowed; only a non-blank value is validated as a Spring cron expression.
     */
    private fun validateGraph(request: BotRequest): Mono<BotRequest> {
        val nodeIds = request.nodes.map { it.id }
        val edgeIds = request.edges.map { it.id }
        val knownNodeIds = nodeIds.toSet()
        val error = when {
            request.nodes.count { it.type == "trigger" } != 1 -> "must have exactly one trigger node"
            nodeIds.size != knownNodeIds.size -> "node ids must be unique"
            edgeIds.size != edgeIds.toSet().size -> "edge ids must be unique"
            request.edges.any { it.source !in knownNodeIds || it.target !in knownNodeIds } ->
                "edges must reference existing nodes"
            !isValidSchedule(request.schedule) -> "invalid cron schedule"
            else -> null
        }
        return error?.let { Mono.error(InvalidRequestException("Bot", it)) } ?: Mono.just(request)
    }

    /**
     * A null/blank schedule is "not scheduled" (valid); any other value must be a parseable Spring
     * cron expression.
     */
    private fun isValidSchedule(schedule: String?): Boolean =
            schedule.isNullOrBlank() || CronExpression.isValidExpression(schedule)

    private fun findOwnedOrFail(id: String, ownerId: String): Mono<BotTemplate> {
        return botTemplateRepository.findById(id)
                .switchIfEmpty(Mono.error(InvalidRequestException("Bot", "not found")))
                .flatMap { bot ->
                    if (bot.ownerId != ownerId) {
                        Mono.error(InvalidRequestException("Bot", "not found"))
                    } else {
                        Mono.just(bot)
                    }
                }
    }
}

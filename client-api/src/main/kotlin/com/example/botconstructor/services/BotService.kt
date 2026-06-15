package com.example.botconstructor.services

import com.example.botconstructor.dto.BotRequest
import com.example.botconstructor.dto.BotView
import com.example.botconstructor.dto.toView
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.model.BotTemplate
import com.example.botconstructor.repos.BotTemplateRepository
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
        val bot = request.toBotTemplate(UUID.randomUUID().toString(), ownerId)
        return botTemplateRepository.save(bot)
                .map { it.toView() }
    }

    /**
     * Lists all bots owned by [ownerId].
     */
    fun list(ownerId: String): Flux<BotView> {
        return botTemplateRepository.findByOwnerId(ownerId)
                .map { it.toView() }
    }

    /**
     * Gets a single bot by [id], verifying it belongs to [ownerId].
     */
    fun get(id: String, ownerId: String): Mono<BotView> {
        return findOwnedOrFail(id, ownerId)
                .map { it.toView() }
    }

    /**
     * Updates the bot [id] owned by [ownerId] from [request].
     */
    fun update(id: String, request: BotRequest, ownerId: String): Mono<BotView> {
        return findOwnedOrFail(id, ownerId)
                .map { request.applyTo(it) }
                .flatMap { botTemplateRepository.save(it) }
                .map { it.toView() }
    }

    /**
     * Deletes the bot [id] owned by [ownerId].
     */
    fun delete(id: String, ownerId: String): Mono<Void> {
        return findOwnedOrFail(id, ownerId)
                .flatMap { botTemplateRepository.delete(it) }
    }

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

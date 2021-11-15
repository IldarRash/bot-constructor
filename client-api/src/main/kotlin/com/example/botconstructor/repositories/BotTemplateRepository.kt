package com.example.botconstructor.repositories

import com.example.botconstructor.bot_template.BotTemplate
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface BotTemplateRepository : ReactiveCrudRepository<BotTemplate, Long> {

    fun findAllByOwnerId(ownerId: String) : Flux<BotTemplate>

    fun findBotTemplateById(id: Long) : Mono<BotTemplate>
}

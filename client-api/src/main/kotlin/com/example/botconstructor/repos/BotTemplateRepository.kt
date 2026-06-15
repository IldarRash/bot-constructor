package com.example.botconstructor.repos

import com.example.botconstructor.model.BotTemplate
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux

interface BotTemplateRepository : ReactiveMongoRepository<BotTemplate, String> {
    fun findByOwnerId(ownerId: String): Flux<BotTemplate>
}

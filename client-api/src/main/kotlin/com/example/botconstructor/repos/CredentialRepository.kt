package com.example.botconstructor.repos

import com.example.botconstructor.model.Credential
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface CredentialRepository : ReactiveMongoRepository<Credential, String> {

    /**
     * Resolves a credential by id only when it belongs to [ownerId], so a forged/foreign id yields an
     * empty result indistinguishable from a missing one (no IDOR leak between not-found and not-owned).
     */
    fun findByIdAndOwnerId(id: String, ownerId: String): Mono<Credential>

    /** All credentials owned by [ownerId]. */
    fun findByOwnerId(ownerId: String): Flux<Credential>
}

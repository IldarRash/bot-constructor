package com.example.botconstructor.repos

import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.model.User
import org.springframework.data.mongodb.repository.ReactiveMongoRepository
import reactor.core.publisher.Mono


interface UserRepository : ReactiveMongoRepository<User, String> {
    fun findByEmail(email: String): Mono<User>

    fun existsByEmail(email: String): Mono<Boolean>

    fun existsByUsername(username: String): Mono<Boolean>

    fun findByUsername(username: String): Mono<User>
}

fun UserRepository.findByUsernameOrFail(username: String): Mono<User> {
    return findByUsername(username)
            .switchIfEmpty(Mono.error(InvalidRequestException("Username", "not found")))
}

fun UserRepository.findByEmailOrFail(email: String): Mono<User> {
    return findByEmail(email)
            .switchIfEmpty(Mono.error(InvalidRequestException("Email", "not found")))
}


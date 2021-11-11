package com.example.botconstructor.services

import com.example.botconstructor.TokenPrincipal
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.model.User
import com.example.botconstructor.repos.UserRepository
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class UserSessionProvider(private val userRepository: UserRepository) {

    fun getCurrentUserSessionOrFail() =
            getCurrentUserSessionOrNull()
                    .switchIfEmpty(Mono.error(InvalidRequestException("User", "current user is not login in")))

    fun getCurrentUserSessionOrNull(): Mono<UserSession?> {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap {
                    if (it.authentication == null)
                        return@flatMap Mono.empty<UserSession>()
                    val token = it.authentication.principal as TokenPrincipal
                    return@flatMap userRepository.findById(token.userId)
                            .map { UserSession(it, token.token) }
                }
    }
}

data class UserSession(
        val user: User,
        val token: String,
)

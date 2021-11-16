package com.example.botconstructor.services

import com.example.botconstructor.dto.UpdateUserRequest
import com.example.botconstructor.dto.UserView
import com.example.botconstructor.dto.toUserView
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.repositories.UserRepository
import com.example.botconstructor.repositories.findByEmailOrFail
import com.example.botconstructor.security.PasswordService
import com.example.botconstructor.security.UserAuthenticationRequest
import com.example.botconstructor.security.UserRegistrationRequest
import com.example.botconstructor.security.UserTokenProvider
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.util.function.Tuple2
import java.util.*

@Component
class UserService(
        private val userRepository: UserRepository,
        private val passwordService: PasswordService,
        private val userTokenProvider: UserTokenProvider,
) {

    fun signup(request: UserRegistrationRequest): Mono<UserView> {
        val encodedPassword = passwordService.encodePassword(request.password)
        val id = UUID.randomUUID().toString()
        val user = request.toUser(encodedPassword, id)
        return userRepository.existsByEmail(request.email)
                .zipWith(userRepository.existsByUsername(request.username))
                .flatMap { alreadyInUserException(it) }
                .flatMap {
                    userRepository.save(user)
                            .map {
                                val token = userTokenProvider.getToken(it.id)
                                it.toUserView(token)
                            }
                }

    }

    fun login(request: UserAuthenticationRequest): Mono<UserView> {
        return userRepository.findByEmailOrFail(request.email)
                .map {
                    if (!passwordService.matches(rowPassword = request.password, encodedPassword = it.encodedPassword)) {
                        throw InvalidRequestException("Password", "invalid")
                    }
                    it
                }
                .map {
                    val token = userTokenProvider.getToken(it.id)
                    it.toUserView(token)
                }
    }

    fun updateUser(request: UpdateUserRequest, userSession: UserSession): Mono<UserView> {
        val (user, token) = userSession
        return userRepository.save(user)
                .map { it.toUserView(token) }

    }

    fun alreadyInUserException(tuple: Tuple2<Boolean?, Boolean?>): Mono<Tuple2<Boolean?, Boolean?>> {
        if (tuple.t1)
            return Mono.error { InvalidRequestException("Email", "already in use") }
        if (tuple.t2)
            return Mono.error { InvalidRequestException("Username", "already in use") }
        return Mono.just(tuple)
    }

}

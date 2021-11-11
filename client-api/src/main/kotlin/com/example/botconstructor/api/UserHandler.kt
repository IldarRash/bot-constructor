package com.example.botconstructor.api

import com.example.botconstructor.dto.*
import com.example.botconstructor.services.UserService
import com.example.botconstructor.services.UserSessionProvider
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.*
import reactor.core.publisher.Mono
import javax.validation.Valid

@Service
class UserHandler(
        private val userService: UserService,
        private val userSessionProvider: UserSessionProvider
) {

    fun signup(serverRequest: ServerRequest): Mono<ServerResponse> {
        return serverRequest
                .bodyToMono(UserRegistrationRequest::class.java)
                .flatMap {
                    userService.signup(it)
                }.flatMap {
                    ok().body(it, UserView::class.java)
                }.onErrorResume {
                    badRequest().bodyValue(it.localizedMessage)
                }
    }

    fun login(serverRequest: ServerRequest): Mono<ServerResponse> {
        return serverRequest
                .bodyToMono(UserAuthenticationRequest::class.java)
                .flatMap { userService.login(it) }
                .flatMap { ok().body(it, UserView::class.java) }
    }

    fun getCurrentUser(serverRequest: ServerRequest): Mono<ServerResponse> {
        return userSessionProvider
                .getCurrentUserSessionOrFail()
                .flatMap {
                    ok().body(
                            it?.user?.toUserView(it.token)!!,
                            UserView::class.java)
                }
                .onErrorResume {
                    badRequest().bodyValue(it.localizedMessage)
                }
    }

    @PutMapping("/user")
    fun updateUser(serverRequest: ServerRequest): Mono<ServerResponse> {
        return userSessionProvider.getCurrentUserSessionOrFail()
                .zipWith(serverRequest
                        .bodyToMono(UpdateUserRequest::class.java))
                .flatMap { userService.updateUser(it.t2, it.t1) }
                .flatMap {
                    ok().body(
                            it,
                            UserView::class.java)
                }
                .onErrorResume {
                    badRequest().bodyValue(it.localizedMessage)
                }
    }
}

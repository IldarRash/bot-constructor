package com.example.botconstructor.api

import com.example.botconstructor.dto.UpdateUserRequest
import com.example.botconstructor.dto.UserView
import com.example.botconstructor.dto.toUserView
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.security.UserAuthenticationRequest
import com.example.botconstructor.security.UserRegistrationRequest
import com.example.botconstructor.services.UserService
import com.example.botconstructor.services.UserSessionProvider
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.badRequest
import org.springframework.web.reactive.function.server.ServerResponse.ok
import reactor.core.publisher.Mono

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
                    badRequest().bodyValue(invalidRequestExceptionHandler(it as InvalidRequestException))
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
                    badRequest().bodyValue(invalidRequestExceptionHandler(it as InvalidRequestException))
                }
    }

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
                    badRequest().bodyValue(invalidRequestExceptionHandler(it as InvalidRequestException))
                }
    }


    protected fun invalidRequestExceptionHandler(e: InvalidRequestException): InvalidRequestExceptionResponse {
        val subject = e.subject
        val violation = e.violation
        val errors = mapOf(subject to listOf(violation))
        return InvalidRequestExceptionResponse(errors)
    }

    data class InvalidRequestExceptionResponse(val errors: Map<String, List<String>>)

}

package com.example.botconstructor.api

import com.example.botconstructor.dto.*
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.services.UserService
import com.example.botconstructor.services.UserSessionProvider
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.badRequest
import org.springframework.web.reactive.function.server.ServerResponse.ok
import reactor.core.publisher.Mono

/**
 * Handles user-related HTTP requests.
 *
 * @property userService The service for user-related operations.
 * @property userSessionProvider The provider for user session information.
 */
@Service
class UserHandler(
        private val userService: UserService,
        private val userSessionProvider: UserSessionProvider
) {

    /**
     * Handles user registration requests.
     * It takes a [UserRegistrationRequest] from the request body and uses the [UserService] to create a new user.
     *
     * @param serverRequest The incoming server request.
     * @return A [ServerResponse] with the created [UserView] or an error response.
     */
    fun signup(serverRequest: ServerRequest): Mono<ServerResponse> {
        return serverRequest
                .bodyToMono(UserRegistrationRequest::class.java)
                .flatMap {
                    userService.signup(it)
                }.flatMap {
                    ok().bodyValue(it)
                }.onErrorResume(::handleInvalidRequestException)
    }

    /**
     * Handles user login requests.
     * It takes a [UserAuthenticationRequest] from the request body and uses the [UserService] to authenticate the user.
     *
     * @param serverRequest The incoming server request.
     * @return A [ServerResponse] with the authenticated [UserView] or an error response.
     */
    fun login(serverRequest: ServerRequest): Mono<ServerResponse> {
        return serverRequest
                .bodyToMono(UserAuthenticationRequest::class.java)
                .flatMap { userService.login(it) }
                .flatMap { ok().bodyValue(it) }
    }

    /**
     * Retrieves the currently authenticated user's information.
     *
     * @param serverRequest The incoming server request.
     * @return A [ServerResponse] with the current [UserView] or an error response.
     */
    fun getCurrentUser(serverRequest: ServerRequest): Mono<ServerResponse> {
        return userSessionProvider
                .getCurrentUserSessionOrFail()
                .flatMap { userSession ->
                    userSession.user?.let {
                        ok().bodyValue(it.toUserView(userSession.token))
                    } ?: Mono.error(InvalidRequestException("user", "not found"))
                }
                .onErrorResume(::handleInvalidRequestException)
    }

    /**
     * Handles requests to update the current user's information.
     *
     * @param serverRequest The incoming server request, containing the [UpdateUserRequest] in its body.
     * @return A [ServerResponse] with the updated [UserView] or an error response.
     */
    fun updateUser(serverRequest: ServerRequest): Mono<ServerResponse> {
        return userSessionProvider.getCurrentUserSessionOrFail()
                .zipWith(serverRequest.bodyToMono(UpdateUserRequest::class.java))
                .flatMap { (userSession, updateUserRequest) ->
                    userService.updateUser(updateUserRequest, userSession)
                }
                .flatMap {
                    ok().bodyValue(it)
                }
                .onErrorResume(::handleInvalidRequestException)
    }


    private fun handleInvalidRequestException(exception: Throwable): Mono<ServerResponse> {
        return if (exception is InvalidRequestException) {
            val response = InvalidRequestExceptionResponse(mapOf(exception.subject to listOf(exception.violation)))
            badRequest().bodyValue(response)
        } else {
            Mono.error(exception)
        }
    }

    private data class InvalidRequestExceptionResponse(val errors: Map<String, List<String>>)
}

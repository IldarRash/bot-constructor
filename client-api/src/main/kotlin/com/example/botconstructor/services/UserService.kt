package com.example.botconstructor.services

import com.example.botconstructor.dto.*
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.model.User
import com.example.botconstructor.repos.UserRepository
import com.example.botconstructor.repos.findByEmailOrFail
import com.example.botconstructor.security.UserTokenProvider
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.util.function.Tuple2
import java.util.*

/**
 * Service for user-related operations like registration, login, and updates.
 *
 * @property userRepository The repository for accessing user data.
 * @property passwordService The service for handling password encoding and verification.
 * @property userTokenProvider The provider for generating user authentication tokens.
 */
@Component
class UserService(
        private val userRepository: UserRepository,
        private val passwordService: PasswordService,
        private val userTokenProvider: UserTokenProvider,
) {

    /**
     * Registers a new user.
     *
     * @param request The user registration request containing user details.
     * @return A [Mono] containing the [UserView] of the newly created user.
     * @throws InvalidRequestException if the email or username is already in use.
     */
    fun signup(request: UserRegistrationRequest): Mono<UserView> {
        return userRepository.existsByEmail(request.email)
                .zipWith(userRepository.existsByUsername(request.username))
                .flatMap { (emailExists, usernameExists) ->
                    if (emailExists) {
                        Mono.error(InvalidRequestException("Email", "already in use"))
                    } else if (usernameExists) {
                        Mono.error(InvalidRequestException("Username", "already in use"))
                    } else {
                        val encodedPassword = passwordService.encodePassword(request.password)
                        val id = UUID.randomUUID().toString()
                        val user = request.toUser(encodedPassword, id)
                        userRepository.save(user)
                    }
                }
                .map { savedUser ->
                    val token = userTokenProvider.getToken(savedUser.id)
                    savedUser.toUserView(token)
                }

    }

    /**
     * Authenticates a user and returns a [UserView] with a new token.
     *
     * @param request The user authentication request containing email and password.
     * @return A [Mono] containing the [UserView] of the authenticated user.
     * @throws InvalidRequestException if the password is invalid.
     */
    fun login(request: UserAuthenticationRequest): Mono<UserView> {
        return userRepository.findByEmailOrFail(request.email)
                .flatMap { user ->
                    if (!passwordService.matches(rowPassword = request.password, encodedPassword = user.encodedPassword)) {
                        Mono.error(InvalidRequestException("Password", "invalid"))
                    } else {
                        val token = userTokenProvider.getToken(user.id)
                        Mono.just(user.toUserView(token))
                    }
                }
    }

    /**
     * Updates an existing user's information.
     *
     * @param request The request containing the new user data.
     * @param userSession The session of the user to be updated.
     * @return A [Mono] containing the updated [UserView].
     */
    fun updateUser(request: UpdateUserRequest, userSession: UserSession): Mono<UserView> {
        val userToUpdate = userSession.user
        val updatedUser = userToUpdate.copy(
                username = request.username ?: userToUpdate.username,
                email = request.email ?: userToUpdate.email,
                bio = request.bio ?: userToUpdate.bio,
                image = request.image ?: userToUpdate.image
        )
        if (request.password != null) {
            updatedUser.encodedPassword = passwordService.encodePassword(request.password)
        }

        return userRepository.save(updatedUser)
                .map { it.toUserView(userSession.token) }

    }
}

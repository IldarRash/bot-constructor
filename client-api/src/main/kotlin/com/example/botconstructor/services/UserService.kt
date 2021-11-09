package com.example.botconstructor.services

import com.example.botconstructor.dto.*
import com.example.botconstructor.exceptions.InvalidRequestException
import com.example.botconstructor.model.User
import com.example.botconstructor.repos.UserRepository
import com.example.botconstructor.repos.findByEmailOrFail
import com.example.botconstructor.repos.findByUsernameOrFail
import com.example.botconstructor.security.UserTokenProvider
import com.realworld.springmongo.user.UserSession
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.stereotype.Component
import java.util.*

@Component
class UserService(
        private val userRepository: UserRepository,
        private val passwordService: PasswordService,
        private val userTokenProvider: UserTokenProvider,
) {

    suspend fun signup(request: UserRegistrationRequest): UserView {
        if (userRepository.existsByEmail(request.email).awaitSingle()) {
            throw emailAlreadyInUseException()
        }
        if (userRepository.existsByUsername(request.username).awaitSingle()) {
            throw usernameAlreadyInUseException()
        }
        val encodedPassword = passwordService.encodePassword(request.password)
        val id = UUID.randomUUID().toString()
        val user = request.toUser(encodedPassword, id)
        return userRepository.save(user)
                .map {
                    val token = userTokenProvider.getToken(it.id)
                    it.toUserView(token)
                }.awaitSingle()
    }

    suspend fun login(request: UserAuthenticationRequest): UserView {
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
                }.awaitSingle()
    }

    suspend fun updateUser(request: UpdateUserRequest, userSession: UserSession): UserView {
        val (user, token) = userSession
        updateUser(request, user)
        val savedUser = userRepository.save(user).awaitSingle()
        return savedUser.toUserView(token)
    }

    private suspend fun updateUser(request: UpdateUserRequest, user: User) {
        request.bio?.let { user.bio = it }
        request.image?.let { user.image = it }
        request.password?.let { user.encodedPassword = passwordService.encodePassword(it) }
        request.username?.let { updateUsername(user, it) }
        request.email?.let { updateEmail(user, it) }
    }

    suspend fun getProfile(username: String, viewer: User?): ProfileView = userRepository
            .findByUsernameOrFail(username)
            .awaitSingle()
            .toProfileView(viewer)

    private suspend fun updateUsername(user: User, newUsername: String) {
        if (user.username == newUsername) {
            return
        }
        if (userRepository.existsByUsername(newUsername).awaitSingle()) {
            throw usernameAlreadyInUseException()
        }
        user.username = newUsername
    }

    private suspend fun updateEmail(user: User, newEmail: String) {
        if (user.email == newEmail) {
            return
        }
        if (userRepository.existsByEmail(newEmail).awaitSingle()) {
            throw emailAlreadyInUseException()
        }
        user.email = newEmail
    }

    private fun usernameAlreadyInUseException() = InvalidRequestException("Username", "already in use")

    private fun emailAlreadyInUseException() = InvalidRequestException("Email", "already in use")
}

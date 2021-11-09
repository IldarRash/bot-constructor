package com.realworld.springmongo.api

import com.example.botconstructor.dto.*
import com.example.botconstructor.services.UserService
import com.realworld.springmongo.user.UserSessionProvider
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import javax.validation.Valid

@RestController
@RequestMapping("/api")
class UserController(private val userService: UserService, private val userSessionProvider: UserSessionProvider) {

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun signup(@RequestBody @Valid request: UserWrapper<UserRegistrationRequest>): UserWrapper<UserView> {
        return userService.signup(request.content).toUserWrapper()
    }

    @PostMapping("/users/login")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun login(@RequestBody @Valid request: UserWrapper<UserAuthenticationRequest>): UserWrapper<UserView> {
        return userService.login(request.content).toUserWrapper()
    }

    @GetMapping("/user")
    suspend fun getCurrentUser(): UserWrapper<UserView> {
        val (user, token) = userSessionProvider.getCurrentUserSessionOrFail()
        return user.toUserView(token).toUserWrapper()
    }

    @PutMapping("/user")
    suspend fun updateUser(@RequestBody @Valid request: UserWrapper<UpdateUserRequest>): UserWrapper<UserView> {
        val userContext = userSessionProvider.getCurrentUserSessionOrFail()
        return userService.updateUser(request.content, userContext).toUserWrapper()
    }

    @GetMapping("/profiles/{username}")
    suspend fun getProfile(@PathVariable username: String): ProfileWrapper {
        val currentUser = userSessionProvider.getCurrentUserOrNull()
        return userService.getProfile(username, currentUser).toProfileWrapper()
    }
}

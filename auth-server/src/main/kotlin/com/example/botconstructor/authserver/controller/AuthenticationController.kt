package com.example.botconstructor.authserver.controller

import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import reactor.core.publisher.Mono

@Controller
class AuthenticationController {
    @MessageMapping("authenticate")
    fun authenticate(@AuthenticationPrincipal user: Mono<UserDetails>): Mono<UserDetails> {
        return user
    }
} 
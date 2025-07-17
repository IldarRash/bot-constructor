package com.example.botconstructor.authserver.service

import org.springframework.context.annotation.Bean
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service

@Service
class UserService {
    @Bean
    fun userDetailsService(): MapReactiveUserDetailsService {
        val user: UserDetails = User.withDefaultPasswordEncoder()
                .username("user")
                .password("password")
                .roles("USER")
                .build()
        return MapReactiveUserDetailsService(user)
    }
} 
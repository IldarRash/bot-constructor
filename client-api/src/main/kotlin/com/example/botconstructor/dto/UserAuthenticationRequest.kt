package com.example.botconstructor.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class UserAuthenticationRequest(
        @field:Email
        @field:NotBlank
        val email: String,
        @field:NotBlank
        val password: String,
)

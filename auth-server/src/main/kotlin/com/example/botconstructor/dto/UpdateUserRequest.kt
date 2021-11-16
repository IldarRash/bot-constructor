package com.example.botconstructor.dto

import javax.validation.constraints.Email
import javax.validation.constraints.NotBlank

data class UpdateUserRequest(
        @field:Email
        @field:NotBlank
        val email: String?,
        @field:NotBlank
        val username: String?,
        @field:NotBlank
        val password: String?,
        val image: String?,
        val bio: String?,
)

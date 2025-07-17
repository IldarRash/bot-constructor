package com.example.botconstructor.dto

import com.realworld.springmongo.validation.NotBlankOrNull
import javax.validation.constraints.Email

/**
 * Data transfer object for updating a user.
 * All fields are optional.
 *
 * @property email The user's new email.
 * @property username The user's new username.
 * @property password The user's new password.
 * @property image The user's new image URL.
 * @property bio The user's new bio.
 */
data class UpdateUserRequest(
        @field:Email
        @field:NotBlankOrNull
        val email: String?,
        @field:NotBlankOrNull
        val username: String?,
        @field:NotBlankOrNull
        val password: String?,
        val image: String?,
        val bio: String?,
)

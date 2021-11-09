package com.example.botconstructor.dto

import com.example.botconstructor.model.User

data class UserView(
    val email: String,
    val token: String,
    val username: String,
    val bio: String?,
    val image: String?,
)

fun User.toUserView(token: String) = UserView(
    email = this.email,
    token = token,
    username = this.username,
    bio = this.bio,
    image = this.image,
)

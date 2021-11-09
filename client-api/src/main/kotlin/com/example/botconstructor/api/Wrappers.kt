package com.example.botconstructor.api

import com.example.botconstructor.dto.ProfileView
import com.fasterxml.jackson.annotation.JsonProperty

data class UserWrapper<T>(@JsonProperty("user") val content: T)

data class ProfileWrapper(@JsonProperty("profile") val content: ProfileView)

fun <T> T.toUserWrapper() = UserWrapper(this)

fun ProfileView.toProfileWrapper() = ProfileWrapper(this)

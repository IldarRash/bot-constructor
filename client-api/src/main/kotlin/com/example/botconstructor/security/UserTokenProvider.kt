package com.example.botconstructor.security

interface UserTokenProvider {
    fun getToken(userId: String): String
}

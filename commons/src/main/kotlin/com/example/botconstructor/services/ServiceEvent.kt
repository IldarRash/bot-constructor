package com.example.botconstructor.services

enum class ServiceType {
    BOT_API, BOT_CREATOR_ANSWER, CLIENT_API
}

data class Service(val id: String, val host: String, val port: Int, val serviceType: ServiceType)




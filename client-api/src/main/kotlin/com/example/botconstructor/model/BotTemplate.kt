package com.example.botconstructor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

enum class BotType { Instagram, Vkontakte, Telegram }

@Document("bot_template")
data class BotTemplate(
        @Id
        val id: String,
        val name: String,
        val type: BotType,
        val ownerId: String,
        val questions: List<Question>,
        val fallbackAnswer: String,
)

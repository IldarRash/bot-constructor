package com.example.botconstructor.dto

import com.example.botconstructor.model.BotTemplate
import com.example.botconstructor.model.BotType
import com.example.botconstructor.model.Question
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank

/**
 * Request body for creating or updating a bot.
 *
 * @property name The bot's display name.
 * @property type The bot platform type (Instagram, Vkontakte, Telegram).
 * @property questions The questions the bot reacts to, each with its trigger key words and answer.
 * @property fallbackAnswer The answer returned when no question matches.
 */
data class BotRequest(
        @field:NotBlank
        val name: String,
        val type: BotType,
        @field:Valid
        val questions: List<BotQuestion>,
        @field:NotBlank
        val fallbackAnswer: String,
) {
    /**
     * Builds a brand new [BotTemplate] owned by [ownerId] with the given [id].
     */
    fun toBotTemplate(id: String, ownerId: String) = BotTemplate(
            id = id,
            name = name,
            type = type,
            ownerId = ownerId,
            questions = questions.map { it.toQuestion() },
            fallbackAnswer = fallbackAnswer,
    )

    /**
     * Applies this request onto an existing [bot], preserving its id and owner.
     */
    fun applyTo(bot: BotTemplate) = bot.copy(
            name = name,
            type = type,
            questions = questions.map { it.toQuestion() },
            fallbackAnswer = fallbackAnswer,
    )
}

data class BotQuestion(
        @field:NotBlank
        val text: String,
        val keyWords: List<String>,
        @field:NotBlank
        val answer: String,
) {
    fun toQuestion() = Question(text = text, keyWords = keyWords, answer = answer)
}

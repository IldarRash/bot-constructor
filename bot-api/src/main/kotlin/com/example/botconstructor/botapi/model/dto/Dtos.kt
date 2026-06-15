package com.example.botconstructor.botapi.model.dto

/**
 * A single question of a bot, mirroring what client-api returns.
 *
 * @property text The human-readable question text.
 * @property keyWords The trigger key words for this question.
 * @property answer The answer returned when this question matches.
 */
data class QuestionSummary(
        val text: String,
        val keyWords: List<String>,
        val answer: String,
)

/**
 * The bot as returned by client-api's GET /api/bots/{id}.
 *
 * @property id The bot id.
 * @property name The bot's display name.
 * @property type The bot platform type.
 * @property questions The questions the bot reacts to.
 * @property fallbackAnswer The answer returned when no question matches.
 */
data class BotSummary(
        val id: String,
        val name: String,
        val type: String,
        val questions: List<QuestionSummary>,
        val fallbackAnswer: String,
)

/**
 * Response for starting a runtime session.
 *
 * @property sessionId The opaque id used to send subsequent messages.
 * @property greeting The bot's opening line.
 */
data class StartSessionResponse(
        val sessionId: String,
        val greeting: String,
)

/**
 * Body of a message sent to a runtime session.
 *
 * @property text The user's message text.
 */
data class MessageRequest(
        val text: String,
)

/**
 * Reply produced by the runtime engine.
 *
 * @property reply The answer text to show the user.
 * @property matched The matched question (only its text), or null when the fallback was used.
 */
data class MessageResponse(
        val reply: String,
        val matched: MatchedQuestion?,
)

/**
 * The matched question exposed to clients.
 *
 * @property text The matched question's text.
 */
data class MatchedQuestion(
        val text: String,
)

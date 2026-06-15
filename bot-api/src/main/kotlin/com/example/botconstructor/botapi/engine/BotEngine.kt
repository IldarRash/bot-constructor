package com.example.botconstructor.botapi.engine

import com.example.botconstructor.botapi.model.dto.QuestionSummary

/**
 * Outcome of running the engine over a user message.
 *
 * @property reply The answer text to return.
 * @property matched The question that matched, or null when the fallback answer was used.
 */
data class MatchResult(
        val reply: String,
        val matched: QuestionSummary?,
)

/**
 * Pure, Spring-free matching engine.
 *
 * A question matches when ANY of its key words appears in the user text, case-insensitively,
 * either as a whole token or as a substring of the text. The first matching question (in list
 * order) wins; if none match, the bot's [fallbackAnswer] is returned.
 */
object BotEngine {

    private val tokenSplit = Regex("\\W+")

    fun reply(text: String, questions: List<QuestionSummary>, fallbackAnswer: String): MatchResult {
        val normalized = text.lowercase()
        val tokens = normalized.split(tokenSplit).filter { it.isNotBlank() }.toSet()

        val matched = questions.firstOrNull { question ->
            question.keyWords.any { keyWord ->
                val needle = keyWord.lowercase().trim()
                needle.isNotEmpty() && (needle in tokens || normalized.contains(needle))
            }
        }

        return if (matched != null) {
            MatchResult(reply = matched.answer, matched = matched)
        } else {
            MatchResult(reply = fallbackAnswer, matched = null)
        }
    }
}

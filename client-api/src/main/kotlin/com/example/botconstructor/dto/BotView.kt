package com.example.botconstructor.dto

import com.example.botconstructor.model.BotTemplate
import com.example.botconstructor.model.Question

/**
 * Response view of a bot, returned to the owner.
 */
data class BotView(
        val id: String,
        val name: String,
        val type: String,
        val ownerId: String,
        val questions: List<QuestionView>,
        val fallbackAnswer: String,
)

data class QuestionView(
        val text: String,
        val keyWords: List<String>,
        val answer: String,
)

fun BotTemplate.toView() = BotView(
        id = id,
        name = name,
        type = type.name,
        ownerId = ownerId,
        questions = questions.map { it.toView() },
        fallbackAnswer = fallbackAnswer,
)

fun Question.toView() = QuestionView(text = text, keyWords = keyWords, answer = answer)

package com.example.botconstructor.botapi.engine

import com.example.botconstructor.botapi.model.dto.QuestionSummary
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BotEngineTest {

    private val fallback = "Sorry, I did not understand"

    private val questions = listOf(
            QuestionSummary(
                    text = "Greeting",
                    keyWords = listOf("hi", "hello"),
                    answer = "Hello there!",
            ),
            QuestionSummary(
                    text = "Pricing",
                    keyWords = listOf("price", "cost"),
                    answer = "It is free.",
            ),
    )

    @Test
    fun `matches a question when a key word appears as a whole word`() {
        val result = BotEngine.reply("Hi, how are you?", questions, fallback)

        assertThat(result.reply).isEqualTo("Hello there!")
        assertThat(result.matched?.text).isEqualTo("Greeting")
    }

    @Test
    fun `matches case-insensitively`() {
        val result = BotEngine.reply("HELLO everyone", questions, fallback)

        assertThat(result.reply).isEqualTo("Hello there!")
        assertThat(result.matched?.text).isEqualTo("Greeting")
    }

    @Test
    fun `matches a key word as a substring of a longer word in the text`() {
        // "cost" is a genuine substring of "costs"
        val result = BotEngine.reply("what are the costs", questions, fallback)

        assertThat(result.reply).isEqualTo("It is free.")
        assertThat(result.matched?.text).isEqualTo("Pricing")
    }

    @Test
    fun `returns the first matching question in list order`() {
        val result = BotEngine.reply("hi, what is the price?", questions, fallback)

        assertThat(result.matched?.text).isEqualTo("Greeting")
    }

    @Test
    fun `falls back when no key word matches`() {
        val result = BotEngine.reply("totally unrelated message", questions, fallback)

        assertThat(result.reply).isEqualTo(fallback)
        assertThat(result.matched).isNull()
    }

    @Test
    fun `falls back when there are no questions`() {
        val result = BotEngine.reply("hello", emptyList(), fallback)

        assertThat(result.reply).isEqualTo(fallback)
        assertThat(result.matched).isNull()
    }
}

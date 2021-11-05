package com.example.botconstructor.model

sealed class BotType {

    object Instagram : BotType()
    object Vkontakte : BotType()
}

interface BotTemplate {
    fun getBotType(): BotType
}


data class InstagramBot(val id: Long, val name: String, val ownerId: Long, val questions: BotQuestions, val answer: BotAnswer) : BotTemplate {
    override fun getBotType(): BotType {
        return BotType.Instagram
    }
}


data class VkontakteBot(val id: Long, val name: String, val ownerId: Long, val questions: BotQuestions, val answer: BotAnswer) : BotTemplate {
    override fun getBotType(): BotType {
        return BotType.Vkontakte
    }
}

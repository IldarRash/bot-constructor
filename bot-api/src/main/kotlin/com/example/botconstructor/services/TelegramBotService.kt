package com.example.botconstructor.services


import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class TelegramBotService {
    /*private val botMap = ConcurrentHashMap<String, Bot>()

    fun startBot(token: String, botTemplate: String) {
        val bot = Bot.createPolling(botTemplate, token)

        bot.chain("/start") { msg -> bot.sendMessage(msg.chat.id, "Hi! What is your name?") }
            .then { msg -> bot.sendMessage(msg.chat.id, "Nice to meet you, ${msg.text}! Send something to me") }
            .then { msg -> bot.sendMessage(msg.chat.id, "Fine! See you soon") }
            .build()

        botMap.put(token, bot)
    }

    fun stopBot(botId: String)  = botMap[botId]?.stop()

    fun createChainFromTemplate() = {
        TODO("Create Chain")
    }*/
}


package com.example.botconstructor.repositories

import com.example.botconstructor.model.Bot
import io.rsocket.Payload
import io.rsocket.util.ByteBufPayload.create
import io.rsocket.util.DefaultPayload
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.concurrent.ConcurrentHashMap

@Service
class BotAnswerRepositories(val botMap: ConcurrentHashMap<String, Bot> = ConcurrentHashMap()) {

    fun register(bot: Bot) {
        if (!botMap.containsKey(bot.id)) {
            botMap[bot.id] = bot
        }
    }

    fun getBot(id: String): Bot {
        return botMap[id]!!
    }
}

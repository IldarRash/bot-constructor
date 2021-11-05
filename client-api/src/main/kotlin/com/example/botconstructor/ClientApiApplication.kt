package com.example.botconstructor

import com.example.botconstructor.model.BotAnswer
import com.example.botconstructor.model.BotQuestions
import com.example.botconstructor.model.InstagramBot
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ClientApiApplication

fun main(args: Array<String>) {
    runApplication<ClientApiApplication>(*args)
}

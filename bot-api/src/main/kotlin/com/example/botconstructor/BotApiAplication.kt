package com.example.botconstructor

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class BotApiApplication

fun main(args: Array<String>) {
    runApplication<BotApiApplication>(*args)
}

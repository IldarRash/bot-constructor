package com.example.botconstructor.botapi

import com.example.botconstructor.botapi.config.NativeHints
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ImportRuntimeHints

@SpringBootApplication
@ImportRuntimeHints(NativeHints::class)
class BotApiApplication

fun main(args: Array<String>) {
    runApplication<BotApiApplication>(*args)
}

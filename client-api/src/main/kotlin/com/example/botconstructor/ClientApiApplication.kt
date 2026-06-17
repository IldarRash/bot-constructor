package com.example.botconstructor

import com.example.botconstructor.config.NativeHints
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ImportRuntimeHints

@SpringBootApplication
@ImportRuntimeHints(NativeHints::class)
class ClientApiApplication

fun main(args: Array<String>) {
    runApplication<ClientApiApplication>(*args)
}

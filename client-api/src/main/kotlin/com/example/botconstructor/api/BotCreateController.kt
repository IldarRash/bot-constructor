package com.example.botconstructor.api

import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/api")
class BotCreateController(val rSocketRequester: RSocketRequester) {

    @PostMapping("/telegram")
    fun authTelegram(@RequestBody token: String) {
        rSocketRequester.route(
            "bot.authorize.telegram"
        ).data(token)
            .retrieveMono(Boolean.javaClass)
            .subscribe()
    }

}

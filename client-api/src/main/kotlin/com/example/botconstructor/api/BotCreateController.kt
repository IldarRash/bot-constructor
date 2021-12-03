package com.example.botconstructor.api

import io.rsocket.broker.client.spring.BrokerRSocketRequester
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api")
class BotCreateController(val brokerRSocketRequester: BrokerRSocketRequester) {

    @PostMapping("/telegram")
    fun authTelegram(@RequestBody token: String) {
        brokerRSocketRequester.route(
            "bot.authorize.telegram"
        ).data(token)
            .retrieveMono(Boolean.javaClass)
            .subscribe()
    }

}

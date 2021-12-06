package com.example.botconstructor.api

import com.example.botconstructor.dto.Event
import com.example.botconstructor.model.BotTemplate
import com.example.botconstructor.services.BotTemplateService
import org.springframework.messaging.handler.annotation.DestinationVariable
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.messaging.rsocket.RSocketRequester
import org.springframework.messaging.rsocket.annotation.ConnectMapping
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Controller
@MessageMapping("bot.template")
class BotTemplateApi(val botService: BotTemplateService) {



    @MessageMapping("event")
    fun getEvent(@Payload event: String, rSocketRequester: RSocketRequester) =
        Mono.error<java.lang.RuntimeException>(RuntimeException())


    @MessageMapping("new")
    fun newTemplate(@Payload name: String): Mono<BotTemplate> {
        return botService.newTemplate(name)
    }

    @MessageMapping("{id}.edit")
    fun editTemplate(@DestinationVariable("id") id: String, events: Flux<Event>): Flux<Event> {
        return botService.editTemplate(events, id)
    }
}

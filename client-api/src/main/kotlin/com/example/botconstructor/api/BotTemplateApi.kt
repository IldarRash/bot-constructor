package com.example.botconstructor.api

import com.example.botconstructor.model.BotTemplate
import com.example.botconstructor.dto.Event
import com.example.botconstructor.services.BotTemplateService
import org.springframework.messaging.handler.annotation.MessageMapping
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Controller
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Controller
@MessageMapping("bot.template")
class BotTemplateApi(val botService: BotTemplateService) {

    @MessageMapping("new")
    fun newTemplate(@Payload name: String): Mono<BotTemplate> {

        return botService.newTemplate(name)
    }

    @MessageMapping("edit")
    fun editTempalte(events: Flux<Event>): Flux<Event> {
        return botService.editTemplate(events)
    }
}

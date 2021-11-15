package com.example.botconstructor.config

import com.example.botconstructor.dto.Event
import com.example.botconstructor.dto.EventType
import com.example.botconstructor.services.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class BotConfiguration {

    @Bean
    fun validators(): Map<EventType, Validator<Event>> {
        return mapOf(
                EventType.TEMPLATE to TemplateValidator(),
                EventType.EDGE to EdgeValidator(),
                EventType.NODE to NodeValidator()
        ) as Map<EventType, Validator<Event>>
    }

    @Bean
    fun editors(): Map<EventType, Editor<Event>> {
        return mapOf(
                EventType.TEMPLATE to TemplateEditor(),
                EventType.EDGE to EdgeEditor(),
                EventType.NODE to NodeEditor()
        ) as Map<EventType, Editor<Event>>
    }
}

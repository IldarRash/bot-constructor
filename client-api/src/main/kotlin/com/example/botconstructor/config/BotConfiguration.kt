package com.example.botconstructor.config

import com.example.botconstructor.bot_template.EdgeValidator
import com.example.botconstructor.bot_template.NodeValidator
import com.example.botconstructor.bot_template.TemplateValidator
import com.example.botconstructor.bot_template.Validator
import com.example.botconstructor.dto.Event
import com.example.botconstructor.dto.EventType
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
}

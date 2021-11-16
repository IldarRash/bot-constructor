package com.example.botconstructor.services

import com.example.botconstructor.dto.*
import com.example.botconstructor.model.BotTemplate
import org.springframework.stereotype.Component

data class Valid(val message: String, val valid: Boolean)
interface Validator<T : Event> {
    fun validateEvent(data: T, botTemplate: BotTemplate): Pair<T, Valid>
}

@Component
class EdgeValidator : Validator<EdgeEvent> {
    override fun validateEvent(data: EdgeEvent, botTemplate: BotTemplate): Pair<EdgeEvent, Valid> {
        val check = botTemplate.edges.stream()
                .filter { it == data.toEdge() }
                .findAny()
        if (check.isPresent) {
            return data to Valid("Такая нода уже есть", false)
        }
        return data to Valid("", true)
    }
}

@Component
class NodeValidator : Validator<NodeEvent> {
    override fun validateEvent(data: NodeEvent, botTemplate: BotTemplate): Pair<NodeEvent, Valid> {
        return data to Valid("", true)
    }
}

@Component
class TemplateValidator : Validator<TemplateEvent> {
    override fun validateEvent(data: TemplateEvent, botTemplate: BotTemplate): Pair<TemplateEvent, Valid> {
        return data to Valid("", true)
    }
}

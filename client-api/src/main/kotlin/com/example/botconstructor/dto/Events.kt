package com.example.botconstructor.dto

import com.example.botconstructor.bot_template.Button
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

enum class EventType {
    ERROR, EMPTY, NODE, EDGE, TEMPLATE
}

enum class EditType {
    SAVE, CLOSE
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "EventType")
@JsonSubTypes(
        JsonSubTypes.Type(value = NodeEvent::class, name = "NODE"),
        JsonSubTypes.Type(value = ErrorEvent::class, name = "ERROR"),
        JsonSubTypes.Type(value = EdgeEvent::class, name = "EDGE"),
        JsonSubTypes.Type(value = TemplateEvent::class, name = "TEMPLATE"),
        JsonSubTypes.Type(value = EmptyEvent::class, name = "EMPTY"),
)
interface Event {
    fun getType(): EventType
}

data class NodeEvent(
        val id: Int,
        val textMessage: String,
        val inputCounts: Short,
        val outputCounts: Short,
        val hasButtons: Boolean,
        val buttons: List<Button>,
        val attachUrl: String
) : Event {
    override fun getType(): EventType = EventType.NODE
}

data class EdgeEvent(
        val id: Int,
        val target: Short,
        val source: Short
) : Event {
    override fun getType(): EventType = EventType.EDGE
}

data class TemplateEvent(
        val id: String,
        val name: String,
        val editType: EditType
) : Event {
    override fun getType(): EventType = EventType.TEMPLATE
}

data class ErrorEvent(
        val id: String,
        val message: String,
        val errorType: EventType
) : Event {
    override fun getType(): EventType = EventType.ERROR
}

class EmptyEvent : Event {
    override fun getType(): EventType = EventType.EMPTY
}

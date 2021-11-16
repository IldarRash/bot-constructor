package com.example.botconstructor.dto

import com.example.botconstructor.model.Button
import com.example.botconstructor.model.Edge
import com.example.botconstructor.model.Node
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

enum class EventType {
    ERROR, EMPTY, NODE, EDGE, TEMPLATE
}

enum class EditType {
    SAVE, CLOSE
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
        JsonSubTypes.Type(value = NodeEvent::class, name = "node"),
        JsonSubTypes.Type(value = ErrorEvent::class, name = "error"),
        JsonSubTypes.Type(value = EdgeEvent::class, name = "edge"),
        JsonSubTypes.Type(value = TemplateEvent::class, name = "template"),
        JsonSubTypes.Type(value = EmptyEvent::class, name = "empty"),
)
interface Event {

    @JsonIgnore
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

fun NodeEvent.toNode() : Node {
    return Node(id, textMessage, inputCounts, outputCounts, hasButtons, buttons, attachUrl)
}

data class EdgeEvent(
        val id: Int,
        val target: Short,
        val source: Short
) : Event {
    override fun getType(): EventType = EventType.EDGE
}

fun EdgeEvent.toEdge() : Edge {
    return Edge(id, target, source)
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
        val message: String
) : Event {
    override fun getType(): EventType = EventType.ERROR
}

class EmptyEvent : Event {
    override fun getType(): EventType = EventType.EMPTY
}

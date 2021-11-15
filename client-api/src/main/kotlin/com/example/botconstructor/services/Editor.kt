package com.example.botconstructor.services

import com.example.botconstructor.dto.*
import com.example.botconstructor.model.BotTemplate
import org.springframework.stereotype.Component

interface Editor<T : Event>  {
    fun editTemplate(data: T, botTemplate: BotTemplate): T
}

fun <T : Event> Editor<T>.editTemplate(data: Pair<T, Valid>, botTemplate: BotTemplate): Pair<T, Valid> {
        if (data.second.valid)
            return editTemplate(data.first, botTemplate) to data.second
        return data
}

@Component
class EdgeEditor : Editor<EdgeEvent> {
    override fun editTemplate(data: EdgeEvent, botTemplate: BotTemplate): EdgeEvent {
        botTemplate.addEdges(data.toEdge())
        return data
    }
}

@Component
class NodeEditor : Editor<NodeEvent> {
    override fun editTemplate(data: NodeEvent, botTemplate: BotTemplate): NodeEvent {
        botTemplate.addNode(data.toNode())
        return data
    }
}

@Component
class TemplateEditor : Editor<TemplateEvent> {
    override fun editTemplate(data: TemplateEvent, botTemplate: BotTemplate): TemplateEvent {
        botTemplate.empty = data.editType == EditType.SAVE
        botTemplate.name = data.name
        return data
    }
}

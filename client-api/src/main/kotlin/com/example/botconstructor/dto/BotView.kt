package com.example.botconstructor.dto

import com.example.botconstructor.model.BotTemplate
import com.example.botconstructor.model.FlowEdge
import com.example.botconstructor.model.FlowNode

/**
 * Response view of a bot, returned to the owner. Always exposes a graph (legacy documents are
 * normalized before mapping).
 *
 * @property webhookToken The bot's unguessable webhook secret. Exposed only to the authenticated
 * owner on the single-bot GET / create / update so they can build the inbound webhook URL; it is
 * stripped from the list response to avoid handing out every token at once. Never accepted on input
 * — see [BotRequest], which has no token field.
 */
data class BotView(
        val id: String,
        val name: String,
        val type: String,
        val ownerId: String,
        val nodes: List<NodeView>,
        val edges: List<EdgeView>,
        val fallbackAnswer: String,
        val webhookToken: String?,
        val schedule: String? = null,
)

data class NodeView(
        val id: String,
        val type: String,
        val position: PositionView,
        val data: Map<String, Any?>,
)

data class PositionView(val x: Double, val y: Double)

data class EdgeView(
        val id: String,
        val source: String,
        val target: String,
        val sourceHandle: String?,
)

fun BotTemplate.toView(): BotView {
    val graph = normalized()
    return BotView(
            id = graph.id,
            name = graph.name,
            type = graph.type.name,
            ownerId = graph.ownerId,
            nodes = graph.nodes.map { it.toView() },
            edges = graph.edges.map { it.toView() },
            fallbackAnswer = graph.fallbackAnswer,
            webhookToken = graph.webhookToken,
            schedule = graph.schedule,
    )
}

fun FlowNode.toView() = NodeView(
        id = id,
        type = type,
        position = PositionView(position.x, position.y),
        data = data,
)

fun FlowEdge.toView() = EdgeView(id = id, source = source, target = target, sourceHandle = sourceHandle)

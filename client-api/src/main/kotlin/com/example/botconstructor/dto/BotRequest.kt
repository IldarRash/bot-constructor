package com.example.botconstructor.dto

import com.example.botconstructor.model.BotTemplate
import com.example.botconstructor.model.BotType
import com.example.botconstructor.model.FlowEdge
import com.example.botconstructor.model.FlowNode
import com.example.botconstructor.model.NodePosition
import com.example.botconstructor.model.generateWebhookToken
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Request body for creating or updating a bot.
 *
 * @property name The bot's display name.
 * @property type The bot platform type (Instagram, Vkontakte, Telegram).
 * @property nodes The flow graph's typed nodes (must contain exactly one `trigger`).
 * @property edges The flow graph's edges wiring node outputs to inputs.
 * @property fallbackAnswer The answer returned when the flow produces no reply.
 * @property schedule Optional Spring cron expression that schedules periodic runs of this bot's
 * flow. Owner-controlled (unlike the webhook token). Null/blank means "not scheduled"; a non-blank
 * value is validated as a cron expression in [com.example.botconstructor.services.BotService].
 */
data class BotRequest(
        @field:NotBlank
        val name: String,
        val type: BotType,
        @field:Valid
        @field:Size(max = 500, message = "a flow may contain at most 500 nodes")
        val nodes: List<NodeRequest> = emptyList(),
        @field:Valid
        @field:Size(max = 1000, message = "a flow may contain at most 1000 edges")
        val edges: List<EdgeRequest> = emptyList(),
        @field:NotBlank
        val fallbackAnswer: String,
        val schedule: String? = null,
) {
    /**
     * Builds a brand new [BotTemplate] owned by [ownerId] with the given [id]. The webhook token is
     * minted here server-side (never taken from the request body) so clients can't choose it.
     */
    fun toBotTemplate(id: String, ownerId: String) = BotTemplate(
            id = id,
            name = name,
            type = type,
            ownerId = ownerId,
            nodes = nodes.map { it.toNode() },
            edges = edges.map { it.toEdge() },
            fallbackAnswer = fallbackAnswer,
            webhookToken = generateWebhookToken(),
            schedule = schedule?.takeIf { it.isNotBlank() },
            legacyQuestions = null,
    )

    /**
     * Applies this request onto an existing [bot], preserving its id, owner, and webhook token.
     * The request intentionally has no token field, so an update can never overwrite the secret.
     */
    fun applyTo(bot: BotTemplate) = bot.copy(
            name = name,
            type = type,
            nodes = nodes.map { it.toNode() },
            edges = edges.map { it.toEdge() },
            fallbackAnswer = fallbackAnswer,
            schedule = schedule?.takeIf { it.isNotBlank() },
            legacyQuestions = null,
    )
}

data class NodeRequest(
        @field:NotBlank
        val id: String,
        @field:NotBlank
        val type: String,
        val position: PositionRequest = PositionRequest(),
        val data: Map<String, Any?> = emptyMap(),
) {
    fun toNode() = FlowNode(
            id = id,
            type = type,
            position = NodePosition(position.x, position.y),
            data = data,
    )
}

data class PositionRequest(val x: Double = 0.0, val y: Double = 0.0)

data class EdgeRequest(
        @field:NotBlank
        val id: String,
        @field:NotBlank
        val source: String,
        @field:NotBlank
        val target: String,
        val sourceHandle: String? = null,
) {
    fun toEdge() = FlowEdge(id = id, source = source, target = target, sourceHandle = sourceHandle)
}

package com.example.botconstructor.botapi.model.dto

/**
 * A node in a bot's workflow graph, mirroring React Flow's node shape as returned by client-api.
 *
 * @property id The node id, unique within the graph.
 * @property type The node type (e.g. `trigger`, `keyword`, `sendMessage`).
 * @property position The editor position (cosmetic; the runtime ignores it).
 * @property data An open per-type config bag (e.g. `keyWords`, `text`, `label`).
 */
data class FlowNode(
        val id: String,
        val type: String,
        val position: NodePosition = NodePosition(),
        val data: Map<String, Any?> = emptyMap(),
)

/**
 * Editor coordinates for a [FlowNode].
 *
 * @property x The horizontal position.
 * @property y The vertical position.
 */
data class NodePosition(
        val x: Double = 0.0,
        val y: Double = 0.0,
)

/**
 * A directed edge between two [FlowNode]s.
 *
 * @property id The edge id, unique within the graph.
 * @property source The id of the node the edge leaves.
 * @property target The id of the node the edge enters.
 * @property sourceHandle The output handle the edge leaves from (e.g. `match`/`nomatch`),
 *   or null for the default output.
 */
data class FlowEdge(
        val id: String,
        val source: String,
        val target: String,
        val sourceHandle: String? = null,
)

/**
 * The bot as returned by client-api's GET /api/bots/{id}.
 *
 * @property id The bot id.
 * @property name The bot's display name.
 * @property type The bot platform type.
 * @property nodes The workflow graph nodes.
 * @property edges The workflow graph edges.
 * @property fallbackAnswer The answer returned when the walk produces no reply.
 */
data class BotSummary(
        val id: String,
        val name: String,
        val type: String,
        val nodes: List<FlowNode> = emptyList(),
        val edges: List<FlowEdge> = emptyList(),
        val fallbackAnswer: String,
)

/**
 * Response for starting a runtime session.
 *
 * @property sessionId The opaque id used to send subsequent messages.
 * @property greeting The bot's opening line.
 */
data class StartSessionResponse(
        val sessionId: String,
        val greeting: String,
)

/**
 * Body of a message sent to a runtime session.
 *
 * @property text The user's message text.
 */
data class MessageRequest(
        val text: String,
)

/**
 * Body of an inbound webhook invocation (`POST /api/runtime/webhooks/{token}`). The flow runs once,
 * statelessly — there is no session.
 *
 * @property message The text that seeds the engine's user input (`vars["message"]`); treated as the
 *   empty string when null.
 * @property vars Extra variables merged into the execution context as initial variables before the
 *   walk. `message` is always seeded from [message] and cannot be overridden here.
 */
data class WebhookRequest(
        val message: String? = null,
        val vars: Map<String, Any?>? = null,
)

/**
 * One node's contribution to a walk, in execution order — the execution-trace debugger's unit.
 *
 * @property nodeId The id of the node that executed.
 * @property type The node's [FlowNode.type].
 * @property handle The output handle the walk followed from this node
 *   (`match`/`nomatch`/`true`/`false`/`error`), or null for the default output.
 * @property detail A short human-readable summary of what the node did (interpolated reply,
 *   condition outcome, HTTP status, etc.).
 */
data class TraceStep(
        val nodeId: String,
        val type: String,
        val handle: String?,
        val detail: String?,
)

/**
 * Reply produced by the runtime engine.
 *
 * @property reply The answer text to show the user.
 * @property matched The keyword node that fired, or null when the fallback was used.
 * @property trace The per-node execution trace, in execution order (empty on the timeout fallback).
 * @property vars The final variable snapshot after the walk (empty on the timeout fallback).
 */
data class MessageResponse(
        val reply: String,
        val matched: MatchedQuestion?,
        val trace: List<TraceStep> = emptyList(),
        val vars: Map<String, Any?> = emptyMap(),
)

/**
 * The keyword node that fired, exposed to clients.
 *
 * @property text The fired keyword node's label.
 */
data class MatchedQuestion(
        val text: String,
)

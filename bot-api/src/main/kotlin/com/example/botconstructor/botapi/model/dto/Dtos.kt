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
 * Body of a manual editor run (`POST /api/runtime/bots/{id}/execute`). Owner-scoped: the caller's
 * Authorization header is forwarded to client-api so only the bot owner can run it. Runs the saved
 * flow once with trigger `manual`.
 *
 * @property message Seeds the engine's user text (`vars["message"]`); treated as empty when null.
 * @property vars Extra variables merged in as initial variables before the walk; `message` cannot be
 *   overridden here.
 * @property pinnedData Per-node pinned output, keyed by node id, each value a list of `{json}` items.
 *   A pinned node's executor is skipped (no HTTP/connector/code side effect) and these items are
 *   emitted on its default output — the n8n iterate-without-recalling dev loop. Only affects this run.
 */
data class ManualRunRequest(
        val message: String? = null,
        val vars: Map<String, Any?>? = null,
        val pinnedData: Map<String, List<Map<String, Any?>>>? = null,
)

/**
 * Result of a manual editor run. Carries the same reply/match/trace/vars the chat path returns, plus
 * the rich per-node [nodes] (input/output items per handle) so the editor inspects node data inline
 * without a second fetch. The null output handle is serialized as the literal key `"default"`.
 *
 * @property reply The answer text produced.
 * @property matched The keyword node that fired, or null when the fallback was used.
 * @property status `"success"`, or `"error"` only when the walk hit the timeout fallback — the same
 *   value [ExecutionRecordRequest.status] records, so the editor badge can reflect an errored run.
 * @property vars The final variable snapshot after the walk.
 * @property trace The per-node summary trace, in execution order.
 * @property nodes The rich per-node data, projected from the walk's node traces, in execution order.
 */
data class ManualRunResponse(
        val reply: String,
        val matched: MatchedQuestion?,
        val status: String,
        val vars: Map<String, Any?>,
        val trace: List<TraceStep>,
        val nodes: List<ExecutionRecordNode>,
)

/**
 * Internal server-to-server response from client-api's owner-scoped credential decrypt endpoint
 * (`GET /api/internal/credentials/{id}?botId=...`). Carries the decrypted secret — bot-api uses it
 * only to build the outbound connector/httpRequest call and never persists, traces, or logs it.
 *
 * @property type The credential type (e.g. `telegramApi`, `httpBearerAuth`).
 * @property data The decrypted secret field map (e.g. `{botToken: "..."}`).
 */
data class CredentialSecretView(
        val type: String = "",
        val data: Map<String, String> = emptyMap(),
)

/**
 * The keyword node that fired, exposed to clients.
 *
 * @property text The fired keyword node's label.
 */
data class MatchedQuestion(
        val text: String,
)

/**
 * Internal server-to-server body posted by bot-api to client-api after every walk
 * (`POST /api/internal/executions`, gateway-blocked, permitAll in client-api). client-api resolves
 * `ownerId` from the bot document — bot-api never sends an identity. The item payloads carry node
 * data only (NOT node config), so connector tokens/keys in `node.data` are never persisted.
 *
 * @property botId The walked bot's id.
 * @property status `"success"`, or `"error"` only when the walk hit the timeout fallback.
 * @property trigger `"message"` (session chat) or `"webhook"` (webhook + schedule path).
 * @property startedAt ISO-8601 instant the walk began.
 * @property finishedAt ISO-8601 instant the walk resolved.
 * @property message The bounded user/input text that seeded the walk.
 * @property reply The answer text produced.
 * @property nodes The per-node trace, size-capped, in execution order.
 */
data class ExecutionRecordRequest(
        val botId: String,
        val status: String,
        val trigger: String,
        val startedAt: String,
        val finishedAt: String,
        val message: String,
        val reply: String,
        val nodes: List<ExecutionRecordNode>,
)

/**
 * One node's contribution to an [ExecutionRecordRequest]. The null output handle is serialized as the
 * literal string key `"default"`.
 *
 * @property nodeId The node id that executed.
 * @property type The node's [FlowNode.type].
 * @property handle The primary output handle the walk followed, or null for the default output.
 * @property detail A short human-readable summary of what the node did.
 * @property inputItems The (capped) items the node received.
 * @property outputs The (capped) items emitted, keyed by handle; the null handle becomes `"default"`.
 * @property error A node-level error message, or null.
 */
data class ExecutionRecordNode(
        val nodeId: String,
        val type: String,
        val handle: String?,
        val detail: String?,
        val inputItems: List<ExecutionRecordItem>,
        val outputs: Map<String, List<ExecutionRecordItem>>,
        val error: String? = null,
)

/**
 * A single data item in an [ExecutionRecordNode]. Carries item data only — never node config.
 *
 * @property json The item's JSON-shaped data payload.
 * @property binary Optional binary payloads keyed by name (reserved; usually null).
 */
data class ExecutionRecordItem(
        val json: Map<String, Any?>,
        val binary: Map<String, Any?>? = null,
)

package com.example.botconstructor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * A persisted record of a single bot flow run, owner-scoped for later inspection in the UI. Written
 * server-to-server by bot-api after each run (via the internal executions endpoint); the [ownerId]
 * is resolved here in client-api from the bot document, never sent by the runtime, so a run can only
 * ever be attributed to the bot's real owner.
 *
 * @property status Outcome of the run: `"success"` or `"error"`.
 * @property trigger What started the run: `"message"` (session chat), `"webhook"` (webhook path;
 * scheduled runs arrive here too), or `"manual"` (the editor's on-demand Run). A free string — no
 * enum constrains it.
 * @property message The bounded user/input text that drove the run.
 * @property reply The bot's final reply text.
 * @property nodes Per-node traces in execution order; item payloads are pre-bounded by the runtime.
 * @property createdAt Server-side persist timestamp (distinct from the runtime's [startedAt]).
 */
@Document("execution")
data class Execution(
        @Id
        val id: String,
        val botId: String,
        val ownerId: String,
        val status: String,
        val trigger: String,
        val startedAt: Instant,
        val finishedAt: Instant,
        val message: String,
        val reply: String,
        val nodes: List<ExecutionNode> = emptyList(),
        val createdAt: Instant,
)

/**
 * Trace of one node within an [Execution].
 *
 * @property handle The output handle taken from this node (e.g. `"match"`/`"nomatch"`); `null` is the
 * default output.
 * @property detail Optional human-readable note about what the node did.
 * @property inputItems The items fed into the node. Open data structures stored as-is; they carry
 * item json (data) only, never the node's config bag (no secrets/tokens).
 * @property outputs Items emitted per output handle. The default (null) handle is keyed `"default"`.
 * @property error Optional error message when this node failed.
 */
data class ExecutionNode(
        val nodeId: String,
        val type: String,
        val handle: String?,
        val detail: String?,
        val inputItems: List<Map<String, Any?>> = emptyList(),
        val outputs: Map<String, List<Map<String, Any?>>> = emptyMap(),
        val error: String? = null,
)

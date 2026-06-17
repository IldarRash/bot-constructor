package com.example.botconstructor.botapi.engine

import com.example.botconstructor.botapi.model.dto.FlowEdge
import reactor.core.publisher.Mono

/**
 * A single unit of data flowing between nodes — n8n's item model. A node receives a list of items
 * and emits a list of items. [json] is the item's data payload (the thing expressions read as the
 * "current item"); [binary] is reserved for future binary attachments.
 *
 * @property json The item's JSON-shaped data.
 * @property binary Optional binary payloads keyed by name (unused today; reserved for parity).
 */
data class ExecutionItem(
        val json: Map<String, Any?> = emptyMap(),
        val binary: Map<String, Any?>? = null,
) {
    /** Returns a copy with [extra] merged over [json] (later keys win). */
    fun withJson(extra: Map<String, Any?>): ExecutionItem = copy(json = json + extra)
}

/** The items delivered to a node on its input. */
@JvmInline
value class NodeInput(val items: List<ExecutionItem>)

/**
 * A node's output: one bucket of items per output handle. The `null` key is the default output;
 * named keys are branch handles (`match`/`nomatch`, `true`/`false`, `error`, …). The scheduler
 * routes each outgoing edge by its `sourceHandle`, so only edges whose handle has a non-empty bucket
 * carry items downstream — dead branches simply receive nothing.
 */
data class NodeOutput(val buckets: Map<String?, List<ExecutionItem>>) {
    companion object {
        /** All items on the default (`null`) output. */
        fun default(items: List<ExecutionItem>): NodeOutput = NodeOutput(mapOf(null to items))

        /** All items on a single [handle] (`null` = default). */
        fun handle(handle: String?, items: List<ExecutionItem>): NodeOutput =
                NodeOutput(mapOf(handle to items))
    }
}

/**
 * One node's contribution to a walk, captured in execution order. Carries both the legacy summary
 * fields ([handle]/[detail], projected verbatim into the wire `TraceStep`) and the richer
 * [inputItems]/[outputs] needed by the future execution-history data inspector (Phase D).
 *
 * @property nodeId The id of the node that executed.
 * @property type The node's `FlowNode.type`.
 * @property handle The primary output handle the walk followed from this node, or null for default.
 * @property detail A short human-readable summary of what the node did.
 * @property inputItems The items the node received.
 * @property outputs The items the node emitted, per output handle.
 */
data class NodeTrace(
        val nodeId: String,
        val type: String,
        val handle: String?,
        val detail: String?,
        val inputItems: List<ExecutionItem>,
        val outputs: Map<String?, List<ExecutionItem>>,
)

/**
 * Mutable per-walk execution state. Replaces the old single-purpose `ExecutionContext`.
 *
 * The engine is item-based, but the chat path still returns a single shared variable snapshot
 * (`MatchResult.vars`) and an interpolation namespace that predates items. [varsView] reconstructs
 * that shared map: it is seeded from `initialVars` + `message`, and `setVariable`/`httpRequest`
 * mirror their writes into it. Expressions resolve against [itemVars] (the current item's `json`
 * layered over [varsView]), which equals the old shared map for the single-lineage chat flows the
 * existing tests cover.
 *
 * @param initialVars Extra variables seeded before the walk (e.g. webhook payload `vars`). `message`
 *   is seeded last from [userText] so it can never be shadowed.
 * @property userText The raw incoming user message.
 * @property httpCaller The SSRF-hardened outbound HTTP seam, injected by the runtime.
 * @property edges The graph edges (needed by `httpRequest` to decide error-handle routing).
 * @property credentialResolver Resolves a node's `data.credentialId` into a decrypted secret; the
 *   runtime binds it to the current bot (anti-IDOR), tests default to [CredentialResolver.NONE].
 * @property pinnedData Per-node pinned output items keyed by `node.id` (manual-run dev loop only).
 *   When a node id is present here the scheduler emits these items on the node's default output
 *   WITHOUT running its executor — no HTTP/connector/code side effect. Empty for every non-manual path.
 */
class RunContext(
        val userText: String,
        initialVars: Map<String, Any?>,
        val httpCaller: HttpCaller,
        val edges: List<FlowEdge>,
        val credentialResolver: CredentialResolver = CredentialResolver.NONE,
        val pinnedData: Map<String, List<ExecutionItem>> = emptyMap(),
) {
    /** Lowercased user text, for keyword substring matching. */
    val normalized: String = userText.lowercase()

    /** Whole-word tokens of [normalized], for keyword token matching. */
    val tokens: Set<String> =
            normalized.split(EngineFns.tokenSplit).filter { it.isNotBlank() }.toSet()

    val replies: MutableList<String> = mutableListOf()
    val trace: MutableList<NodeTrace> = mutableListOf()

    /** The first keyword that fired (reported as `MatchResult.matched`). */
    var firstMatch: MatchedSummary? = null

    /** Once a keyword fires its `match` handle it consumes the turn; sibling guards are abandoned. */
    var turnConsumed: Boolean = false

    /** Count of `httpRequest` nodes that have performed (or attempted) a call. */
    var httpRequests: Int = 0

    /** Chat-compat reconstruction of the legacy shared `vars` map. */
    val varsView: MutableMap<String, Any?> =
            LinkedHashMap(initialVars).apply { put("message", userText) }

    /**
     * Per-node iteration state for [LoopExecutor][com.example.botconstructor.botapi.engine.LoopExecutor]
     * nodes, keyed by `node.id`. A loop node stashes its remaining batches and the eventual `done`
     * payload here on first entry and consumes it on each re-entry; absence of a key means "first
     * entry". Untouched by every non-loop node, so normal walks never populate it.
     */
    val loopState: MutableMap<String, Any?> = mutableMapOf()

    /** Expression namespace for [item]: the item's `json` layered over [varsView] (item wins). */
    fun itemVars(item: ExecutionItem): Map<String, Any?> = varsView + item.json

    /** Per-walk memo of resolved credential ids, so a repeated node never refetches the secret. */
    private val credentialCache: MutableMap<String, Mono<CredentialSecret>> = mutableMapOf()

    /**
     * Resolves [credentialId] to its decrypted [CredentialSecret] via [credentialResolver], caching
     * the (cold, `.cache()`d) [Mono] per walk so repeated nodes referencing the same id resolve once.
     * A blank id resolves empty without touching the resolver. The cached value is a live secret —
     * callers must only feed it into the outbound HTTP call, never into traces/items/logs.
     */
    fun resolveCredential(credentialId: String): Mono<CredentialSecret> {
        if (credentialId.isBlank()) return Mono.empty()
        return credentialCache.getOrPut(credentialId) {
            credentialResolver.resolve(credentialId).cache()
        }
    }
}

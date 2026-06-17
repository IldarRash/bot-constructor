package com.example.botconstructor.botapi.runtime

import com.example.botconstructor.botapi.client.ClientApiClient
import com.example.botconstructor.botapi.engine.CredentialResolver
import com.example.botconstructor.botapi.engine.HttpCaller
import com.example.botconstructor.botapi.engine.MatchResult
import com.example.botconstructor.botapi.engine.NodeTrace
import com.example.botconstructor.botapi.engine.WorkflowEngine
import com.example.botconstructor.botapi.model.dto.BotSummary
import com.example.botconstructor.botapi.model.dto.ExecutionRecordItem
import com.example.botconstructor.botapi.model.dto.ExecutionRecordNode
import com.example.botconstructor.botapi.model.dto.ExecutionRecordRequest
import com.example.botconstructor.botapi.model.dto.ManualRunRequest
import com.example.botconstructor.botapi.model.dto.ManualRunResponse
import com.example.botconstructor.botapi.model.dto.MatchedQuestion
import com.example.botconstructor.botapi.model.dto.MessageResponse
import com.example.botconstructor.botapi.model.dto.StartSessionResponse
import com.example.botconstructor.botapi.model.dto.WebhookRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory runtime for chat sessions. A session pins the loaded bot definition so messages can
 * be answered without re-fetching from client-api.
 *
 * @property clientApiClient The client used to load bots from client-api.
 * @property httpCaller The hardened HTTP seam passed to the engine for `httpRequest` nodes.
 */
@Service
class RuntimeService(
        private val clientApiClient: ClientApiClient,
        private val httpCaller: HttpCaller,
        private val objectMapper: ObjectMapper,
) {

    /** A live chat session: the pinned bot definition answered against on each message. */
    private data class Session(val bot: BotSummary)

    private val sessions = ConcurrentHashMap<String, Session>()

    /**
     * Loads the bot [botId] (forwarding [authHeader]) and opens a session for it.
     */
    fun startSession(botId: String, authHeader: String?): Mono<StartSessionResponse> {
        return clientApiClient.fetchBot(botId, authHeader)
                .map { bot ->
                    val sessionId = UUID.randomUUID().toString()
                    sessions[sessionId] = Session(bot)
                    StartSessionResponse(sessionId = sessionId, greeting = "Hi, I'm ${bot.name}")
                }
    }

    /**
     * Runs the engine for [text] within session [sessionId]. Tagged as the `"message"` trigger.
     */
    fun handleMessage(sessionId: String, text: String): Mono<MessageResponse> {
        val session = sessions[sessionId]
                ?: return Mono.error(SessionNotFoundException(sessionId))

        return runEngine(session.bot, text, trigger = TRIGGER_MESSAGE).map { it.toMessageResponse() }
    }

    /**
     * Stateless flow invocation for an external caller: resolves the bot bound to [token] from
     * client-api's internal lookup, then runs the graph engine once with no stored session. The
     * [request] message seeds the engine's user text (empty when null) and [WebhookRequest.vars] are
     * merged in as initial variables. An unknown token surfaces as the upstream 404 for the handler
     * to map.
     */
    fun runWebhook(token: String, request: WebhookRequest): Mono<MessageResponse> {
        return clientApiClient.fetchBotByWebhook(token)
                .flatMap { bot ->
                    runEngine(bot, request.message ?: "", TRIGGER_WEBHOOK, request.vars ?: emptyMap())
                            .map { it.toMessageResponse() }
                }
    }

    /**
     * Manual editor run: loads the bot [botId] forwarding [authHeader] (owner-scoped exactly like
     * [startSession], so a non-owner gets the same upstream not-found), then runs the saved flow once
     * with the [TRIGGER_MANUAL] trigger. [ManualRunRequest.vars] seed the walk and
     * [ManualRunRequest.pinnedData] (each `{json}` parsed into an [com.example.botconstructor.botapi.engine.ExecutionItem])
     * skips the pinned nodes' executors. Returns the rich per-node projection so the editor inspects
     * node input/output inline. The execution is persisted (trigger `manual`) fire-and-forget like the
     * other paths; the same 20s walk budget + SSRF/caps apply.
     */
    fun runManual(botId: String, authHeader: String?, request: ManualRunRequest): Mono<ManualRunResponse> {
        val pinned = parsePinnedData(request.pinnedData)
        return clientApiClient.fetchBot(botId, authHeader)
                .flatMap { bot ->
                    runEngine(bot, request.message ?: "", TRIGGER_MANUAL, request.vars ?: emptyMap(), pinned)
                            .map { it.toManualRunResponse() }
                }
    }

    /** Parses the wire pinned-data ({nodeId: [{json}]}) into engine [ExecutionItem]s per node. */
    private fun parsePinnedData(
            pinnedData: Map<String, List<Map<String, Any?>>>?,
    ): Map<String, List<com.example.botconstructor.botapi.engine.ExecutionItem>> {
        if (pinnedData.isNullOrEmpty()) return emptyMap()
        return pinnedData.mapValues { (_, items) ->
            items.map { item ->
                @Suppress("UNCHECKED_CAST")
                val json = (item["json"] as? Map<String, Any?>) ?: emptyMap()
                com.example.botconstructor.botapi.engine.ExecutionItem(json = json)
            }
        }
    }

    /**
     * Runs the workflow engine for [bot] over [text], seeding [initialVars] and applying [pinnedData]
     * (manual path only; empty elsewhere), and resolves to a [RunOutcome] carrying the reply/match/
     * trace/vars plus the rich node traces every caller projects to its own wire DTO. The whole walk
     * is bounded by [WALK_TIMEOUT]; on timeout it yields the bot's safe fallback rather than a 5xx.
     * After the walk resolves it fire-and-forget posts an execution record to client-api (see
     * [emitExecution]) — that post can never block, delay, or fail the user response.
     *
     * @param trigger The label ([TRIGGER_MESSAGE], [TRIGGER_WEBHOOK] or [TRIGGER_MANUAL]) recorded on
     *   the execution.
     */
    private fun runEngine(
            bot: BotSummary,
            text: String,
            trigger: String,
            initialVars: Map<String, Any?> = emptyMap(),
            pinnedData: Map<String, List<com.example.botconstructor.botapi.engine.ExecutionItem>> = emptyMap(),
    ): Mono<RunOutcome> {
        val startedAt = Instant.now()
        // The engine's synchronous drain (pure nodes, incl. sandboxed `{{= }}` JS and the per-item
        // executors) runs eagerly when `run(...)` is invoked, so it must not execute on the Netty
        // event loop. `defer` makes that invocation happen at subscription time, and `subscribeOn`
        // moves subscription onto a bounded-elastic worker — guest code never touches an nio thread.
        // Resolver bound to THIS bot: every credentialId is resolved through client-api's owner-scoped
        // internal decrypt keyed by bot.id, so a graph can never resolve another owner's credential.
        val credentialResolver = CredentialResolver { credId -> clientApiClient.fetchCredential(credId, bot.id) }
        return Mono.defer {
            WorkflowEngine.run(text, bot.nodes, bot.edges, bot.fallbackAnswer, httpCaller, initialVars, credentialResolver, pinnedData)
                    .map { result ->
                        emitExecution(bot.id, STATUS_SUCCESS, trigger, startedAt, text, result.reply, result.nodeTraces)
                        RunOutcome(
                                reply = result.reply,
                                matched = result.matched?.let { MatchedQuestion(it.label) },
                                status = STATUS_SUCCESS,
                                // The engine already records steps as the wire DTO (it imports the dto
                                // package as it does for FlowNode/FlowEdge), so no mapping is needed.
                                trace = result.trace,
                                vars = result.vars,
                                nodeTraces = result.nodeTraces,
                        )
                    }
        }
                .subscribeOn(Schedulers.boundedElastic())
                // Overall walk budget: a slow/hung HTTP node (or a pathological graph) must not pin a
                // request. On timeout return the bot's safe fallback reply, not a 5xx.
                .timeout(WALK_TIMEOUT)
                .onErrorResume(java.util.concurrent.TimeoutException::class.java) {
                    // The walk never produced a trace; record an error execution with no nodes.
                    emitExecution(bot.id, STATUS_ERROR, trigger, startedAt, text, bot.fallbackAnswer, emptyList())
                    Mono.just(RunOutcome(reply = bot.fallbackAnswer, matched = null, status = STATUS_ERROR))
                }
    }

    /**
     * The result of a single engine walk, shared across the message/webhook/manual paths. The chat
     * paths project it to [MessageResponse]; the manual path projects [nodeTraces] into the rich
     * per-node wire shape (size-capped, same projection the persisted record uses) for inline editor
     * inspection.
     */
    private data class RunOutcome(
            val reply: String,
            val matched: MatchedQuestion?,
            val status: String = STATUS_SUCCESS,
            val trace: List<com.example.botconstructor.botapi.model.dto.TraceStep> = emptyList(),
            val vars: Map<String, Any?> = emptyMap(),
            val nodeTraces: List<NodeTrace> = emptyList(),
    ) {
        fun toMessageResponse(): MessageResponse =
                MessageResponse(reply = reply, matched = matched, trace = trace, vars = vars)

        /** Projects the rich node traces into the wire [ExecutionRecordNode] shape (size-capped). */
        fun toManualRunResponse(mapper: ObjectMapper): ManualRunResponse = ManualRunResponse(
                reply = reply,
                matched = matched,
                status = status,
                vars = vars,
                trace = trace,
                nodes = nodeTraces.map { toRecordNode(it, mapper) },
        )
    }

    /** [RunOutcome.toManualRunResponse] using this service's [objectMapper]. */
    private fun RunOutcome.toManualRunResponse(): ManualRunResponse = toManualRunResponse(objectMapper)

    /**
     * Builds the [ExecutionRecordRequest] from the rich [nodeTraces] (applying the per-node size
     * caps) and posts it to client-api as fire-and-forget: it subscribes independently so the post
     * never blocks, delays, or fails the user response, and any error is logged and swallowed.
     */
    private fun emitExecution(
            botId: String,
            status: String,
            trigger: String,
            startedAt: Instant,
            message: String,
            reply: String,
            nodeTraces: List<NodeTrace>,
    ) {
        val record = ExecutionRecordRequest(
                botId = botId,
                status = status,
                trigger = trigger,
                startedAt = startedAt.toString(),
                finishedAt = Instant.now().toString(),
                message = message,
                reply = reply,
                nodes = nodeTraces.map { toRecordNode(it, objectMapper) },
        )
        clientApiClient.postExecution(record)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        {},
                        { error -> log.warn("Failed to post execution record for bot {}: {}", botId, error.toString()) },
                )
    }

    companion object {
        private val log = LoggerFactory.getLogger(RuntimeService::class.java)

        /** Upper bound on a full graph walk (covers all `httpRequest` nodes + engine work). */
        private val WALK_TIMEOUT: Duration = Duration.ofSeconds(20)

        /** Trigger label for the session chat path. */
        private const val TRIGGER_MESSAGE = "message"

        /** Trigger label for the webhook + schedule path. */
        private const val TRIGGER_WEBHOOK = "webhook"

        /** Trigger label for the on-demand editor (manual execute) path. */
        private const val TRIGGER_MANUAL = "manual"

        private const val STATUS_SUCCESS = "success"
        private const val STATUS_ERROR = "error"

        /** Max items kept per node per direction before truncation. */
        private const val MAX_ITEMS = 20

        /**
         * Max serialized byte size kept per item's json before it is replaced by a marker. Bounds the
         * persisted execution doc regardless of HTTP body size, code-node output width, or webhook vars
         * width (item count alone does not bound per-item payload size).
         */
        private const val MAX_ITEM_BYTES = 32 * 1024

        /** The null output-handle key, serialized as the literal string "default" on the wire. */
        private const val DEFAULT_HANDLE = "default"

        /** Projects a rich [NodeTrace] into the wire [ExecutionRecordNode], applying the size caps. */
        private fun toRecordNode(trace: NodeTrace, mapper: ObjectMapper): ExecutionRecordNode = ExecutionRecordNode(
                nodeId = trace.nodeId,
                type = trace.type,
                handle = trace.handle,
                detail = trace.detail,
                inputItems = capItems(trace.inputItems.map { boundItem(it.json, it.binary, mapper) }),
                outputs = trace.outputs.entries.associate { (handle, items) ->
                    (handle ?: DEFAULT_HANDLE) to capItems(items.map { boundItem(it.json, it.binary, mapper) })
                },
        )

        /**
         * Bounds a single item's serialized size: when its json serializes to more than [MAX_ITEM_BYTES]
         * it is replaced by a marker `{__truncated_bytes__: <size>}` (mirroring the count marker), so one
         * fat item (large HTTP body, wide code output, attacker-controlled webhook vars) can't bloat the
         * persisted doc. Binary is dropped alongside a truncated json. A serialization failure is treated
         * as oversized and also replaced.
         */
        private fun boundItem(
                json: Map<String, Any?>,
                binary: Map<String, Any?>?,
                mapper: ObjectMapper,
        ): ExecutionRecordItem {
            val size = try {
                mapper.writeValueAsBytes(json).size
            } catch (_: Exception) {
                MAX_ITEM_BYTES + 1
            }
            return if (size > MAX_ITEM_BYTES) {
                ExecutionRecordItem(json = mapOf("__truncated_bytes__" to size))
            } else {
                ExecutionRecordItem(json, binary)
            }
        }

        /**
         * Caps [items] at [MAX_ITEMS]: keeps the first [MAX_ITEMS] and, when more were present,
         * appends a single marker item `{json:{__truncated__: <omittedCount>}}`.
         */
        private fun capItems(items: List<ExecutionRecordItem>): List<ExecutionRecordItem> {
            if (items.size <= MAX_ITEMS) return items
            val omitted = items.size - MAX_ITEMS
            return items.take(MAX_ITEMS) + ExecutionRecordItem(json = mapOf("__truncated__" to omitted))
        }
    }
}

/**
 * Raised when a message is sent to an unknown or expired session.
 */
class SessionNotFoundException(sessionId: String) :
        RuntimeException("Session not found: $sessionId")

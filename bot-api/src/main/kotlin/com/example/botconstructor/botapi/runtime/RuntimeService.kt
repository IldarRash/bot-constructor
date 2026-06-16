package com.example.botconstructor.botapi.runtime

import com.example.botconstructor.botapi.client.ClientApiClient
import com.example.botconstructor.botapi.engine.HttpCaller
import com.example.botconstructor.botapi.engine.WorkflowEngine
import com.example.botconstructor.botapi.model.dto.BotSummary
import com.example.botconstructor.botapi.model.dto.MatchedQuestion
import com.example.botconstructor.botapi.model.dto.MessageResponse
import com.example.botconstructor.botapi.model.dto.StartSessionResponse
import com.example.botconstructor.botapi.model.dto.WebhookRequest
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
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
     * Runs the engine for [text] within session [sessionId].
     */
    fun handleMessage(sessionId: String, text: String): Mono<MessageResponse> {
        val session = sessions[sessionId]
                ?: return Mono.error(SessionNotFoundException(sessionId))

        return runEngine(session.bot, text)
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
                    runEngine(bot, request.message ?: "", request.vars ?: emptyMap())
                }
    }

    /**
     * Runs the workflow engine for [bot] over [text], seeding [initialVars], and maps the walk
     * result to the wire [MessageResponse]. The whole walk is bounded by [WALK_TIMEOUT]; on timeout
     * it returns the bot's safe fallback reply rather than a 5xx.
     */
    private fun runEngine(
            bot: BotSummary,
            text: String,
            initialVars: Map<String, Any?> = emptyMap(),
    ): Mono<MessageResponse> {
        // The engine's synchronous drain (pure nodes, incl. sandboxed `{{= }}` JS and the per-item
        // executors) runs eagerly when `run(...)` is invoked, so it must not execute on the Netty
        // event loop. `defer` makes that invocation happen at subscription time, and `subscribeOn`
        // moves subscription onto a bounded-elastic worker — guest code never touches an nio thread.
        return Mono.defer {
            WorkflowEngine.run(text, bot.nodes, bot.edges, bot.fallbackAnswer, httpCaller, initialVars)
                    .map { result ->
                        MessageResponse(
                                reply = result.reply,
                                matched = result.matched?.let { MatchedQuestion(it.label) },
                                // The engine already records steps as the wire DTO (it imports the dto
                                // package as it does for FlowNode/FlowEdge), so no mapping is needed.
                                trace = result.trace,
                                vars = result.vars,
                        )
                    }
        }
                .subscribeOn(Schedulers.boundedElastic())
                // Overall walk budget: a slow/hung HTTP node (or a pathological graph) must not pin a
                // request. On timeout return the bot's safe fallback reply, not a 5xx.
                .timeout(WALK_TIMEOUT)
                .onErrorResume(java.util.concurrent.TimeoutException::class.java) {
                    Mono.just(MessageResponse(reply = bot.fallbackAnswer, matched = null))
                }
    }

    companion object {
        /** Upper bound on a full graph walk (covers all `httpRequest` nodes + engine work). */
        private val WALK_TIMEOUT: Duration = Duration.ofSeconds(20)
    }
}

/**
 * Raised when a message is sent to an unknown or expired session.
 */
class SessionNotFoundException(sessionId: String) :
        RuntimeException("Session not found: $sessionId")

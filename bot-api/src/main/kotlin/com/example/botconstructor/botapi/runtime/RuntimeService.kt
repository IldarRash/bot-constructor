package com.example.botconstructor.botapi.runtime

import com.example.botconstructor.botapi.client.ClientApiClient
import com.example.botconstructor.botapi.engine.BotEngine
import com.example.botconstructor.botapi.model.dto.BotSummary
import com.example.botconstructor.botapi.model.dto.MatchedQuestion
import com.example.botconstructor.botapi.model.dto.MessageResponse
import com.example.botconstructor.botapi.model.dto.StartSessionResponse
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory runtime for chat sessions. A session pins the loaded bot definition so messages can
 * be answered without re-fetching from client-api.
 *
 * @property clientApiClient The client used to load bots from client-api.
 */
@Service
class RuntimeService(
        private val clientApiClient: ClientApiClient,
) {

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

        val bot = session.bot
        val result = BotEngine.reply(text, bot.questions, bot.fallbackAnswer)
        return Mono.just(
                MessageResponse(
                        reply = result.reply,
                        matched = result.matched?.let { MatchedQuestion(it.text) },
                ),
        )
    }
}

/**
 * Raised when a message is sent to an unknown or expired session.
 */
class SessionNotFoundException(sessionId: String) :
        RuntimeException("Session not found: $sessionId")

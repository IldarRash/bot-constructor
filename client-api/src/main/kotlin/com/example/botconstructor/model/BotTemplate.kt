package com.example.botconstructor.model

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import java.security.SecureRandom
import java.util.Base64

enum class BotType { Instagram, Vkontakte, Telegram }

/**
 * Generates a fresh, unguessable webhook token: 32 cryptographically-random bytes (256 bits of
 * entropy) from [SecureRandom], URL-safe Base64 without padding. The token IS the secret that
 * authorizes invoking/reading a single bot's flow over the unauthenticated webhook lookup, so it
 * must be high-entropy and server-generated only (never client-controlled).
 */
internal fun generateWebhookToken(): String {
    val bytes = ByteArray(32)
    WEBHOOK_TOKEN_RANDOM.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private val WEBHOOK_TOKEN_RANDOM = SecureRandom()

/**
 * A single node in the bot's flow graph. Mirrors React Flow's native node shape.
 *
 * @property id Stable node id, unique within the graph.
 * @property type Node type from the registry (`trigger`, `keyword`, `sendMessage`, ...).
 * @property position Editor canvas coordinates.
 * @property data Open per-type config bag (e.g. `keyWords`, `text`).
 */
data class FlowNode(
        val id: String,
        val type: String,
        val position: NodePosition = NodePosition(),
        val data: Map<String, Any?> = emptyMap(),
)

/**
 * Editor canvas coordinates for a [FlowNode].
 */
data class NodePosition(val x: Double = 0.0, val y: Double = 0.0)

/**
 * A directed edge between two [FlowNode]s. Mirrors React Flow's native edge shape.
 *
 * @property sourceHandle Selects which output of a multi-output node the edge leaves from
 * (e.g. `"match"`/`"nomatch"`); `null` is the default output.
 */
data class FlowEdge(
        val id: String,
        val source: String,
        val target: String,
        val sourceHandle: String? = null,
)

@Document("bot_template")
data class BotTemplate(
        @Id
        val id: String,
        val name: String,
        val type: BotType,
        val ownerId: String,
        val nodes: List<FlowNode> = emptyList(),
        val edges: List<FlowEdge> = emptyList(),
        val fallbackAnswer: String,
        /**
         * Unguessable, server-generated secret that lets this bot be invoked via an inbound webhook
         * HTTP POST (handled by bot-api) without a user JWT. Knowing the token authorizes running
         * and reading this one bot's flow. Null only for legacy documents written before the
         * feature; [normalized] lazily mints one so old bots converge to a token on first read.
         */
        val webhookToken: String? = null,
        /**
         * Optional Spring cron expression (e.g. `0 0 * * * *`) that schedules this bot's flow to run
         * periodically. Unlike [webhookToken] this IS owner-controlled (set via `BotRequest`). A
         * null/blank value means the bot is not scheduled. Validated on create/update with
         * [org.springframework.scheduling.support.CronExpression]; the single-instance scheduler
         * (`ScheduledFlowRunner`) fires due bots by invoking their webhook path on bot-api.
         */
        val schedule: String? = null,
        /**
         * Legacy flat keyword list from documents written before the graph model. Mapped from the
         * stored `questions` field for backward-compatible reads only; new writes leave it null.
         */
        @Deprecated("Legacy keyword list; use nodes/edges. Kept only to read old documents.")
        @Field("questions")
        val legacyQuestions: List<Question>? = null,
) {

    /**
     * Returns a graph-backed copy of this template that is guaranteed to carry a [webhookToken].
     * When the document still uses the legacy [legacyQuestions] field (no [nodes]), converts each
     * [Question] into a `trigger -> keyword -> sendMessage` chain so the API always exposes a graph.
     * In all cases a [webhookToken] is lazily minted when absent, so legacy/token-less documents
     * converge to a token on first read or update (the caller is expected to persist the result).
     */
    @Suppress("DEPRECATION")
    fun normalized(): BotTemplate {
        val withToken = if (webhookToken.isNullOrBlank()) copy(webhookToken = generateWebhookToken()) else this

        val questions = withToken.legacyQuestions
        if (withToken.nodes.isNotEmpty() || questions.isNullOrEmpty()) {
            return withToken
        }

        val triggerId = "$id-trigger"
        val nodes = mutableListOf(FlowNode(id = triggerId, type = "trigger"))
        val edges = mutableListOf<FlowEdge>()

        questions.forEachIndexed { index, question ->
            val keywordId = "$id-keyword-$index"
            val sendId = "$id-send-$index"
            nodes += FlowNode(
                    id = keywordId,
                    type = "keyword",
                    data = mapOf("label" to question.text, "keyWords" to question.keyWords),
            )
            nodes += FlowNode(
                    id = sendId,
                    type = "sendMessage",
                    data = mapOf("text" to question.answer),
            )
            edges += FlowEdge(id = "$id-e-trigger-$index", source = triggerId, target = keywordId)
            edges += FlowEdge(
                    id = "$id-e-match-$index",
                    source = keywordId,
                    target = sendId,
                    sourceHandle = "match",
            )
        }

        return withToken.copy(nodes = nodes, edges = edges, legacyQuestions = null)
    }
}

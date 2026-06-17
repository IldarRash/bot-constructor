package com.example.botconstructor.scheduling

import com.example.botconstructor.model.BotTemplate
import com.example.botconstructor.repos.BotTemplateRepository
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentHashMap

/**
 * Single-instance reactive scheduler for cron-triggered bots.
 *
 * On a fixed [TICK] cadence it queries every bot that carries a cron [BotTemplate.schedule],
 * decides which are "due" this window (see [isDue]), and fires each due bot by invoking its existing
 * public webhook path on bot-api with an empty JSON body — reusing the webhook runtime path with no
 * engine changes. Firing is fire-and-forget (reactive, non-blocking); the webhook token is never
 * logged.
 *
 * The last fire time per bot is tracked in-memory ([lastFireByBot]) so a bot fires at most once per
 * cron window even though the tick is coarser than the cron resolution.
 *
 * Known limitation: this state is per-instance with no distributed lock, so a multi-replica
 * deployment would double-fire each scheduled bot (once per replica). Acceptable for the MVP and
 * documented in `docs/workflow-engine.md`.
 */
@Component
class ScheduledFlowRunner(
        private val botTemplateRepository: BotTemplateRepository,
        @param:Value("\${BOT_API_URI:http://localhost:8083}") private val botApiUri: String,
) {

    private val log = LoggerFactory.getLogger(ScheduledFlowRunner::class.java)
    // Build the WebClient inline rather than taking a `WebClient.Builder` constructor parameter. A
    // constructor with a Kotlin default/optional parameter forces Spring to instantiate the bean via
    // Kotlin reflection, which fails under GraalVM native image with
    // `KotlinReflectionInternalError: Unresolved class WebClient$Builder`. With only non-optional
    // params, Spring AOT invokes the constructor directly. (Tests exercise the static helpers only.)
    private val webClient: WebClient = WebClient.builder().baseUrl(botApiUri).build()

    /** Last time each bot (by id) was fired, used to avoid double-firing within a cron window. */
    private val lastFireByBot = ConcurrentHashMap<String, Instant>()

    /**
     * Subscribes the reactive tick at startup. Each tick re-queries scheduled bots, computes the due
     * set, and fires them. Errors on a single tick are logged and swallowed so the interval survives.
     */
    @PostConstruct
    fun start() {
        Flux.interval(TICK)
                .onBackpressureDrop()
                .concatMap { runTick(Instant.now()).onErrorResume { error ->
                    log.warn("Scheduler tick failed: {}", error.toString())
                    Mono.empty()
                } }
                .subscribe()
        log.info("ScheduledFlowRunner started (tick every {}s, bot-api at {})", TICK.seconds, botApiUri)
    }

    /**
     * One scheduler tick at [now]: load scheduled bots, pick the due ones (advancing each due bot's
     * last-fire so it won't re-fire this window), and fire each. Returns when all fires are dispatched.
     */
    fun runTick(now: Instant): Mono<Void> {
        return botTemplateRepository.findByScheduleNotNull()
                .collectList()
                .flatMapMany { bots -> Flux.fromIterable(dueBots(bots, lastFireByBot, now)) }
                .doOnNext { lastFireByBot[it.id] = now }
                .flatMap { fire(it) }
                .then()
    }

    /**
     * Fires one bot by POSTing an empty JSON body to its webhook path on bot-api. Fire-and-forget:
     * success logs at debug, failure at warn. The webhook token is never logged. Bots without a
     * webhook token are skipped.
     */
    private fun fire(bot: BotTemplate): Mono<Void> {
        val token = bot.webhookToken
        if (token.isNullOrBlank()) {
            log.warn("Scheduled bot {} has no webhook token; skipping fire", bot.id)
            return Mono.empty()
        }
        return webClient.post()
                .uri("/api/runtime/webhooks/{token}", token)
                .bodyValue(EMPTY_BODY)
                .retrieve()
                .toBodilessEntity()
                .doOnSuccess { log.debug("Fired scheduled bot {}", bot.id) }
                .doOnError { error -> log.warn("Failed to fire scheduled bot {}: {}", bot.id, error.toString()) }
                .onErrorResume { Mono.empty() }
                .then()
    }

    companion object {
        /** How often the scheduler wakes up to evaluate due bots. */
        val TICK: Duration = Duration.ofSeconds(60)

        private val EMPTY_BODY = emptyMap<String, Any?>()

        /**
         * Pure due-selection: returns the bots from [bots] whose cron schedule has elapsed at [now]
         * given their last fire time in [lastFire].
         *
         * A bot is due when its next cron occurrence after `lastFire ?: (now - TICK)` exists and is
         * not after [now]. Anchoring on `now - TICK` for a never-fired bot bounds the look-back to the
         * current window so a freshly-scheduled bot fires on the next matching boundary rather than
         * "catching up" historic occurrences. Bots with a blank or unparseable cron are skipped (a
         * single bad cron can never break the loop).
         */
        fun dueBots(
                bots: List<BotTemplate>,
                lastFire: Map<String, Instant>,
                now: Instant,
        ): List<BotTemplate> = bots.filter { isDue(it.schedule, lastFire[it.id], now) }

        /**
         * True when [cron] has a next occurrence after `lastFire ?: (now - TICK)` that is at or before
         * [now]. A null/blank cron is never due; an invalid cron is treated as not-due (guarded so it
         * cannot throw out of the evaluation loop).
         */
        fun isDue(cron: String?, lastFire: Instant?, now: Instant): Boolean {
            if (cron.isNullOrBlank()) return false
            return try {
                val expression = CronExpression.parse(cron)
                val anchor = lastFire ?: now.minus(TICK)
                val zone = ZoneId.systemDefault()
                val next = expression.next(ZonedDateTime.ofInstant(anchor, zone)) ?: return false
                !next.toInstant().isAfter(now)
            } catch (ex: Exception) {
                false
            }
        }
    }
}

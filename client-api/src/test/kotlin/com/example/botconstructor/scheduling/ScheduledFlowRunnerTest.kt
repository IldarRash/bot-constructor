package com.example.botconstructor.scheduling

import com.example.botconstructor.model.BotTemplate
import com.example.botconstructor.model.BotType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduledFlowRunnerTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun bot(id: String, schedule: String?) = BotTemplate(
            id = id,
            name = id,
            type = BotType.Telegram,
            ownerId = "owner-1",
            fallbackAnswer = "fallback",
            webhookToken = "tok-$id",
            schedule = schedule,
    )

    /** Instant for a wall-clock time today in the system zone, so cron boundaries are deterministic. */
    private fun at(hour: Int, minute: Int, second: Int = 0): Instant =
            ZonedDateTime.now(zone).withHour(hour).withMinute(minute).withSecond(second).withNano(0).toInstant()

    @Test
    fun `a bot whose cron boundary just elapsed is due`() {
        // Every minute at second 0. Previous fire was a minute ago; the :00 boundary is now reached.
        val now = at(10, 5, 0)
        val lastFire = mapOf("b" to at(10, 4, 0))

        val due = ScheduledFlowRunner.dueBots(listOf(bot("b", "0 * * * * *")), lastFire, now)

        assertThat(due.map { it.id }).containsExactly("b")
    }

    @Test
    fun `a not-yet-due bot is not selected`() {
        // Fires hourly at minute 0; it is currently 10:30, last fired at 10:00 — next is 11:00.
        val now = at(10, 30, 0)
        val lastFire = mapOf("b" to at(10, 0, 0))

        val due = ScheduledFlowRunner.dueBots(listOf(bot("b", "0 0 * * * *")), lastFire, now)

        assertThat(due).isEmpty()
    }

    @Test
    fun `an invalid cron is skipped, not thrown`() {
        val now = at(10, 5, 0)

        val due = ScheduledFlowRunner.dueBots(listOf(bot("bad", "not a cron")), emptyMap(), now)

        assertThat(due).isEmpty()
    }

    @Test
    fun `a blank cron is never due`() {
        assertThat(ScheduledFlowRunner.isDue("", null, at(10, 5))).isFalse()
        assertThat(ScheduledFlowRunner.isDue(null, null, at(10, 5))).isFalse()
    }

    @Test
    fun `advancing lastFire prevents double-firing in the same window`() {
        val cron = "0 0 * * * *" // hourly at minute 0
        val now = at(10, 0, 30) // just past the 10:00 boundary

        // First evaluation with no prior fire: due (look-back is now - TICK).
        assertThat(ScheduledFlowRunner.isDue(cron, null, now)).isTrue()

        // After firing, lastFire == now. A second evaluation in the same window must not re-fire,
        // because the next occurrence after 10:00:30 is 11:00.
        assertThat(ScheduledFlowRunner.isDue(cron, now, now)).isFalse()
    }

    @Test
    fun `a freshly scheduled bot fires on the next matching boundary within the window`() {
        // Never fired (lastFire null). Look-back is bounded to now - TICK, so it only catches a
        // boundary inside the current tick window, not historic ones.
        val cron = "0 * * * * *" // every minute at second 0
        val now = at(10, 5, 10) // 10 seconds after the :00 boundary, within the 60s window

        assertThat(ScheduledFlowRunner.isDue(cron, null, now)).isTrue()
    }

    @Test
    fun `TICK is 60 seconds`() {
        assertThat(ScheduledFlowRunner.TICK).isEqualTo(Duration.ofSeconds(60))
    }
}

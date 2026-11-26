package com.hopa.family.reminders

import com.hopa.family.board.FixtureLogs
import com.hopa.family.time.FamilyClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlertRoutingTest {
    private val zone = "Asia/Jerusalem"
    private val seed = FixtureLogs.seed

    /** The seed's afternoon: the critical pickup (urgent) and Mom's swim heads-up (timed). */
    private val plan = ReminderPlanner.plan(seed, ms("2026-11-11T12:00:00.000Z"), ms("2026-11-11T18:00:00.000Z"))

    private fun ms(iso: String) = FamilyClock.millis(iso)

    @Test
    fun `a device that slept shows only the latest attempt of each reminder`() {
        val mine = plan.filter { it.recipientId == "maya" }
        val due = DueAlerts.due(mine, ms("2026-11-11T13:55:00.000Z"), shown = emptySet())
        assertEquals(listOf("pickup-today:2026-11-11.open:a3"), due.map { it.key })
        val later = DueAlerts.due(mine, ms("2026-11-11T13:55:00.000Z"), shown = due.map { it.key }.toSet())
        assertTrue(later.isEmpty())
    }

    @Test
    fun `alerts the board no longer calls for are withdrawn by key`() {
        val shown = setOf("pickup-today:2026-11-11.open:a1", "swim-today:2026-11-11.heads-up:a1")
        val answered = seed.withPartition(
            com.hopa.family.model.Scope.FAMILY,
            seed.scopes.family.copy(
                items = seed.scopes.family.items.map {
                    if (it.id == "pickup-today") it.copy(status = com.hopa.family.model.ItemStatus.DONE) else it
                },
            ),
        )
        val now = ReminderPlanner.plan(answered, ms("2026-11-11T12:00:00.000Z"), ms("2026-11-11T18:00:00.000Z"))
        assertEquals(setOf("pickup-today:2026-11-11.open:a1"), DueAlerts.withdrawn(shown, now))
    }

    @Test
    fun `quiet hours hold timed alerts but not urgent ones, and small alerts are batched`() {
        val swim = plan.first { it.itemId == "swim-today" }
        val pickup = plan.first { it.itemId == "pickup-today" && it.recipientId == "mom" }
        val quiet = QuietHours("16:00", "18:00") // 14:00–16:00 UTC in November
        val routed = AlertRouting.route(listOf(swim, pickup), quiet, ms(swim.at), zone)
        assertEquals(listOf(Notification.Single(pickup)), routed.send)
        assertEquals(listOf(HeldAlert(swim, "2026-11-11T16:00:00.000Z")), routed.held)

        val second = swim.copy(itemId = "other")
        val batched = AlertRouting.route(listOf(swim, second), quiet = null, ms(swim.at), zone)
        assertEquals(listOf(Notification.Batch(listOf(swim, second))), batched.send)
    }

    @Test
    fun `keys are stable and name the reminder`() {
        val first = plan.first { it.itemId == "pickup-today" && it.recipientId == "dad" }
        assertEquals("pickup-today:2026-11-11.open:a1", first.key)
        assertEquals("pickup-today:2026-11-11.open", first.reminderKey)
        assertEquals(plan, ReminderPlanner.plan(seed, ms("2026-11-11T12:00:00.000Z"), ms("2026-11-11T18:00:00.000Z")))
    }
}

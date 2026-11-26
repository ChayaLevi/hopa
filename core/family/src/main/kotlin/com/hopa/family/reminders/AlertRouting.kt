package com.hopa.family.reminders

import com.hopa.family.time.FamilyClock

/** What a device shows now: one alert, or several small ones joined into one. */
sealed interface Notification {
    val alerts: List<PlannedAlert>

    data class Single(val alert: PlannedAlert) : Notification {
        override val alerts: List<PlannedAlert> get() = listOf(alert)
    }

    data class Batch(override val alerts: List<PlannedAlert>) : Notification
}

/** An alert held by quiet hours until [releaseAt]. */
data class HeldAlert(
    val alert: PlannedAlert,
    val releaseAt: String,
)

data class Routed(
    val send: List<Notification>,
    val held: List<HeldAlert>,
)

/**
 * What one member's device does with the alerts due now (Homemind's `routeAlerts`, local here):
 * quiet hours hold everything but urgent alerts until they end, and several small alerts at once
 * become one notification. Urgent alerts always go at once, on their own.
 */
object AlertRouting {
    fun route(due: List<PlannedAlert>, quiet: QuietHours?, nowMs: Long, zone: String): Routed {
        val held = mutableListOf<HeldAlert>()
        val ready = mutableListOf<PlannedAlert>()
        for (alert in due) {
            if (alert.tier != Tier.URGENT && quiet != null && quiet.contains(nowMs, zone)) {
                held += HeldAlert(alert, FamilyClock.iso(quiet.endAfter(nowMs, zone)))
            } else {
                ready += alert
            }
        }
        val send = mutableListOf<Notification>()
        ready.filter { it.tier == Tier.URGENT }.forEach { send += Notification.Single(it) }
        val small = ready.filter { it.tier != Tier.URGENT }
        when {
            small.size == 1 -> send += Notification.Single(small.single())
            small.size > 1 -> send += Notification.Batch(small)
        }
        return Routed(send, held)
    }
}

/**
 * Which planned alerts a device should show now. An alert is due once its time has come and it was
 * not shown before. A device that slept through several attempts of one reminder shows only the
 * latest (Homemind counts them all and sends one).
 */
object DueAlerts {
    fun due(plan: List<PlannedAlert>, nowMs: Long, shown: Set<String>): List<PlannedAlert> = plan
        .filter { FamilyClock.millis(it.at) <= nowMs }
        .groupBy { Triple(it.recipientId, it.reminderKey, it.kind == AlertKind.BACKUP) }
        .values
        .mapNotNull { same ->
            // Anything at or before the last alert shown for this reminder is already covered by it.
            val lastShown = same.filter { it.key in shown }.maxOfOrNull { it.at }
            same.filter { it.key !in shown && (lastShown == null || it.at > lastShown) }
                .maxWithOrNull(compareBy({ it.at }, { it.kind.ordinal }))
        }
        .sortedWith(compareBy({ it.at }, { it.key }))

    /**
     * Shown alerts whose reminder the board no longer calls for: the device withdraws them by key.
     * [plan] is the current plan over a window that covers every shown alert's reminder.
     */
    fun withdrawn(shown: Set<String>, plan: List<PlannedAlert>): Set<String> {
        val live = plan.map { it.reminderKey }.toSet()
        return shown.filter { key -> key.substringBeforeLast(':') !in live }.toSet()
    }
}

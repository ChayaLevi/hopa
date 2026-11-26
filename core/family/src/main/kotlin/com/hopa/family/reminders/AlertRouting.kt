package com.hopa.family.reminders

import com.hopa.family.time.FamilyClock

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

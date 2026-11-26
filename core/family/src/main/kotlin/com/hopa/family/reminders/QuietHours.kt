package com.hopa.family.reminders

import com.hopa.family.time.FamilyClock

/**
 * A member's quiet hours, local `HH:MM` in the family's zone; `from` may be later than `to`
 * (overnight), and equal means none. A device setting of its own member, not on the board.
 * Homemind's `inQuietHours` and `quietHoursEnd`.
 */
data class QuietHours(
    val from: String,
    val to: String,
) {
    fun contains(millis: Long, zone: String): Boolean {
        if (from == to) return false
        val time = FamilyClock.localParts(millis, zone).time
        return if (from < to) time >= from && time < to else time >= from || time < to
    }

    /** The next end of the quiet hours after [millis]. Only meaningful while they are on. */
    fun endAfter(millis: Long, zone: String): Long {
        val local = FamilyClock.localParts(millis, zone)
        val date = if (local.time < to) local.date else FamilyClock.addDays(local.date, 1)
        return FamilyClock.zonedToUtc(date, to, zone)
    }
}

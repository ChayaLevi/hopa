package com.hopa.family.reminders

import com.hopa.family.time.FamilyClock

/** The evening summary: the next 20:00 local at or after an instant (Homemind's `nextSummaryAt`). */
object EveningSummary {
    fun nextAt(millis: Long, zone: String): Long {
        val local = FamilyClock.localParts(millis, zone)
        val date = if (local.time < ReminderTiming.SUMMARY_TIME) local.date else FamilyClock.addDays(local.date, 1)
        return FamilyClock.zonedToUtc(date, ReminderTiming.SUMMARY_TIME, zone)
    }
}

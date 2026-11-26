package com.hopa.family.rules

import com.hopa.family.time.FamilyClock

/** When a weekly rule next happens (Homemind's `nextOccurrence`), and when it prepares its action. */
object RuleSchedule {
    private const val DAYS_IN_WEEK = 7L
    private const val MINUTE = 60_000L

    /** The next occurrence of [trigger] strictly after [nowMs]: its local date and its instant. */
    fun nextOccurrence(trigger: RuleTrigger, nowMs: Long, zone: String): Pair<String, Long> {
        val today = FamilyClock.localParts(nowMs, zone).date
        for (d in 0..DAYS_IN_WEEK) {
            val date = FamilyClock.addDays(today, d)
            if (FamilyClock.weekday(date) != trigger.weekday) continue
            val at = FamilyClock.zonedToUtc(date, trigger.time, zone)
            if (at > nowMs) return date to at
        }
        val date = FamilyClock.addDays(today, DAYS_IN_WEEK)
        return date to FamilyClock.zonedToUtc(date, trigger.time, zone)
    }

    /** When the rule looks at the family's calendar and prepares its action for that occurrence. */
    fun prepareAt(trigger: RuleTrigger, occurrenceMs: Long): Long = occurrenceMs - trigger.leadMinutes * MINUTE
}

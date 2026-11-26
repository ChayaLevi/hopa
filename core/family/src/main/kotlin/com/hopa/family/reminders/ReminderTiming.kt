package com.hopa.family.reminders

/** Homemind's `REMINDER_TIMING`. */
object ReminderTiming {
    const val LEAD_NORMAL_MINUTES = 30L
    const val LEAD_CRITICAL_MINUTES = 60L
    const val MAX_INTERVAL_MINUTES = 10L
    const val MIN_INTERVAL_MINUTES = 1L
    const val CRITICAL_ATTEMPTS = 3
    const val OPEN_ESCALATE_BEFORE_MINUTES = 15L
    const val SNOOZE_MINUTES = 10L
    const val GRACE_AFTER_START_MINUTES = 60L
    const val SUMMARY_TIME = "20:00"
    const val MINUTE = 60_000L
}

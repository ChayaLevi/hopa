package com.hopa.family.reminders

/**
 * "Remind me later", pressed on this device by [memberId] on the reminder [reminderKey] at [at]: no
 * attempt to them until [ReminderTiming.SNOOZE_MINUTES] later, then one more alert. The escalation
 * times do not move. It is local: an answer on the board (confirm, take, done) is what the other
 * devices see.
 */
data class LocalSnooze(
    val memberId: String,
    val reminderKey: String,
    val at: String,
)

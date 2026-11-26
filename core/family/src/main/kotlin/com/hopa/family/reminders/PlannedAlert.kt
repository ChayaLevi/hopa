package com.hopa.family.reminders

import com.hopa.family.model.Scope

/**
 * One alert the board calls for, for one member at one minute. Every device that holds the item's
 * scope computes the same alerts from the same board (D5); each shows only its own member's.
 *
 * [key] is stable: `<itemId>:<date>.<ask>:<step>`, where step is `a1`–`a3` (attempts), `e<n>`
 * (escalation), `b<n>` (backup notice) or `s` (after a snooze). A device schedules a local alarm
 * under it and cancels it by the same key when the board no longer calls for it.
 */
data class PlannedAlert(
    val itemId: String,
    val scope: Scope,
    /** Local date of the occurrence, in the family's zone. */
    val date: String,
    val occurrenceStart: String,
    val ask: ReminderAsk,
    val kind: AlertKind,
    val step: String,
    /** When it fires (ISO, UTC). */
    val at: String,
    val recipientId: String,
    val tier: Tier,
    val itemVersion: Int,
    /** A sensitive item's alert shows no title and no time. */
    val sensitive: Boolean,
    /** A weekly item can't be marked done as a whole, so its alert offers no Done. */
    val recurring: Boolean,
) {
    /** The reminder this alert belongs to: `<itemId>:<date>.<ask>`. */
    val reminderKey: String get() = "$itemId:$date.${ask.wire}"

    val key: String get() = "$reminderKey:$step"
}

/** The family's reminder settings, kept on the devices (not on the board in v1). */
data class ReminderSettings(
    /** Whether the family set a backup contact for unanswered critical reminders. */
    val backupContact: Boolean = false,
)

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

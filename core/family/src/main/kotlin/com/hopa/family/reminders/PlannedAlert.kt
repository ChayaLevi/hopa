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

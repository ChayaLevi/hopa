package com.hopa.family.reminders

/** What a reminder asks, and of whom (members with an account who hold the item's scope). */
internal data class Ask(
    val ask: ReminderAsk,
    val recipients: List<String>,
)

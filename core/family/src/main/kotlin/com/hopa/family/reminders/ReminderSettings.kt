package com.hopa.family.reminders

/** The family's reminder settings, kept on the devices (not on the board in v1). */
data class ReminderSettings(
    /** Whether the family set a backup contact for unanswered critical reminders. */
    val backupContact: Boolean = false,
)

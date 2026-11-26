package com.hopa.family.reminders

/** An alert held by quiet hours until [releaseAt]. */
data class HeldAlert(
    val alert: PlannedAlert,
    val releaseAt: String,
)

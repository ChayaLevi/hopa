package com.hopa.family.reminders

/** What a device shows now: one alert, or several small ones joined into one. */
sealed interface Notification {
    val alerts: List<PlannedAlert>

    data class Single(val alert: PlannedAlert) : Notification {
        override val alerts: List<PlannedAlert> get() = listOf(alert)
    }

    data class Batch(override val alerts: List<PlannedAlert>) : Notification
}

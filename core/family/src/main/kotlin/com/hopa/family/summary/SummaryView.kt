package com.hopa.family.summary

import com.hopa.family.availability.Unavailability

/** One line of "What you need to do": the item, why, and its local date (absent: no date). */
data class SummaryToDo(
    val itemId: String,
    val why: ToDoReason,
    val date: String? = null,
)

/** An event for everyone to know about, on its local date. */
data class SummaryEvent(
    val itemId: String,
    val date: String,
)

/**
 * One member's summary, from their own view only. Group messages that waited for the summary are
 * local to the device and added by the caller (parents only).
 */
data class SummaryView(
    val kind: SummaryKind,
    /** Daily: the evening's local date. Weekly: the first day of the week ahead. */
    val date: String,
    /** The local dates covered, first and last. */
    val from: String,
    val to: String,
    /** When it goes out (the evening's 20:00, ISO). */
    val scheduledAt: String,
    val toDo: List<SummaryToDo>,
    val away: List<Unavailability>,
    val everyone: List<SummaryEvent>,
)

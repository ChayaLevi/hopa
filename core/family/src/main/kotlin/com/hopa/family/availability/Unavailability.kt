package com.hopa.family.availability

/** Who is unavailable when, from one event's occurrence. */
data class Unavailability(
    val memberId: String,
    val eventId: String,
    val date: String,
    val start: String,
    val end: String,
)

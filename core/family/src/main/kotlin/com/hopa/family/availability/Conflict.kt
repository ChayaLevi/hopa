package com.hopa.family.availability

/** An item someone owns while an event in the same scope makes them unavailable. */
data class Conflict(
    val memberId: String,
    val eventId: String,
    val itemId: String,
    /** Local date of the item's clashing occurrence. */
    val date: String,
    val start: String,
    val end: String? = null,
    /** A weekly item: only this occurrence is opened. */
    val recurring: Boolean,
)

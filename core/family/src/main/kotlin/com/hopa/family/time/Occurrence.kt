package com.hopa.family.time

/** One start of an item: its local date in the family's zone, and its start and end instants. */
data class Occurrence(
    val itemId: String,
    val date: String,
    val start: String,
    val end: String? = null,
)

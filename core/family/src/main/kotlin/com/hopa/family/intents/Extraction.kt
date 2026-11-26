package com.hopa.family.intents

import kotlinx.serialization.Serializable

/**
 * What a message or a screenshot says about the family's logistics (Homemind's `Extraction`). It
 * only describes; turning it into items is the caller's job, and the board makes items from
 * screenshot, email and group sources proposals.
 */
@Serializable
data class Extraction(
    val events: List<Event>,
    val preparations: List<Preparation>,
    val requests: List<Request>,
    val signUp: SignUp?,
    /** Entries left out because their quote is not in the source text (made up). Text sources only. */
    val dropped: Int,
) {
    @Serializable
    data class Event(
        val title: String,
        val start: String,
        val end: String? = null,
        val confidence: Double,
        val quote: String,
    )

    @Serializable
    data class Preparation(
        val title: String,
        val due: String? = null,
        val forEvent: String? = null,
        val confidence: Double,
        val quote: String,
    )

    @Serializable
    data class Request(val text: String, val due: String? = null, val confidence: Double, val quote: String)

    @Serializable
    data class SignUp(
        val topic: String,
        val deadline: String? = null,
        val taken: List<Taken>,
        val confidence: Double,
        val quote: String,
    )

    @Serializable
    data class Taken(val who: String, val item: String)
}

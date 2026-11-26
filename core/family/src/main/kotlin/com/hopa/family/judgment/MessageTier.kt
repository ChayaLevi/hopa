package com.hopa.family.judgment

/** How soon the family should see a message from a group. */
enum class MessageTier(val wire: String) {
    /** It breaks quiet hours: an alert at once, to the parents. */
    URGENT("urgent"),

    /** An alert now, held by quiet hours and batched like any small update. */
    TIMED("timed"),

    /** No alert: it waits for the evening summary, and the screens say so and why. */
    SUMMARY("summary"),
}

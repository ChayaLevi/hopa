package com.hopa.runtime

/** What the phone is running, if anything. */
data class RunState(
    val goal: String = "",
    val source: RunSource? = null,
    val cancelRequested: Boolean = false,
) {
    val busy: Boolean get() = source != null
}

/** Who owns the phone for the current run. */
enum class RunSource {
    CHAT,
    DELIVERY,
}

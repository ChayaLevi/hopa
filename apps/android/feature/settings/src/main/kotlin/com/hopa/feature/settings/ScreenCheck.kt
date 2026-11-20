package com.hopa.feature.settings

/** One result of the self-check: what is true, and what to do if it is not. */
data class ScreenCheck(
    val id: String,
    val text: String,
    val ok: Boolean,
    val action: String = "",
    /** True for a neutral note that is not a pass or a failure. */
    val info: Boolean = false,
)

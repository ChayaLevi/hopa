package com.hopa.app.feature.settings

/** One result of the self-check: what is true, and what to do if it is not. */
data class ScreenCheck(
    val id: String,
    val text: String,
    val ok: Boolean,
    val action: String = "",
)

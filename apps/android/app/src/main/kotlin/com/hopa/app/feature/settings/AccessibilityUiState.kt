package com.hopa.app.feature.settings

/** The accessibility page: whether the switch is on, and which build this is. */
data class AccessibilityUiState(
    val allowed: Boolean = true,
    val installedAt: String = "",
)

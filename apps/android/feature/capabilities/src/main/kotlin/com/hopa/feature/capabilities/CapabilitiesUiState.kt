package com.hopa.feature.capabilities

/** What the capabilities page counts, and whether the screen may be read. */
data class CapabilitiesUiState(
    val records: Int = 0,
    val facts: Int = 0,
    val skills: Int = 0,
    val allowed: Boolean = true,
)

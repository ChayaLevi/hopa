package com.hopa.feature.launchpad

import com.hopa.catalog.routing.RouteTarget

/** One catalog entry for this phone: status, surface, rules, and what it offers. */
data class AppCardUiState(
    val target: RouteTarget? = null,
    val surface: String = "Auto",
    val screenAccess: Boolean = false,
    val trusted: Boolean = false,
    val status: String = "",
    val functions: List<String> = emptyList(),
)

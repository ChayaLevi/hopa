package com.hopa.feature.launchpad

import com.hopa.catalog.routing.RouteTarget

/** The catalogue resolved for this phone, plus why it could not be. */
data class LaunchpadUiState(
    val targets: List<RouteTarget> = emptyList(),
    val status: String = "",
)

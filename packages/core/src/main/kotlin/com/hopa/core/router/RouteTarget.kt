package com.hopa.core.router

data class RouteTarget(
    val name: String,
    val display: String,
    val kind: String,
    val bundle: String = "",
    val domains: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val purpose: String = "",
    val actions: List<String> = emptyList(),
    /**
     * How ready this target is on this client: `ready` (it opens here),
     * `unconfirmed` (an id exists but this device does not have it) or `none`.
     */
    val status: String = "",
    /**
     * The identifier the catalog names for this platform, even when it is not
     * installed here. [bundle] is what would actually launch; this is what the
     * entry is called, so a store page can be offered for a missing app.
     */
    val target: String = "",
)

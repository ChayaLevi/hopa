package com.hopa.knowledge

import kotlinx.serialization.Serializable

/** One app the catalog describes, with a launch target per platform. */
@Serializable
data class CatalogApp(
    val id: String,
    val display: String,
    val aliases: List<String>,
    val purpose: String,
    val actions: List<String>,
    val harmonyos: CatalogTarget,
    val android: CatalogTarget,
    val windows: CatalogTarget,
)

/** One platform's launch target; the same shape on every platform. */
@Serializable
data class CatalogTarget(
    val id: String,
    val detail: String,
    val verified: Boolean,
)

/** One website the catalog describes. */
@Serializable
data class CatalogSite(
    val id: String,
    val display: String,
    val domains: List<String>,
    val aliases: List<String>,
    val validated: String,
    val purpose: String,
    val actions: List<String>,
)

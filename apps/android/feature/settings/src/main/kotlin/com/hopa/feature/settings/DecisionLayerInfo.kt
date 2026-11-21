package com.hopa.feature.settings

/** What the Decisions page shows about one layer; no behaviour, no vendor types. */
data class DecisionLayerInfo(
    val id: String,
    val title: String,
    val detail: String,
    val summary: String,
    val howTo: String,
)

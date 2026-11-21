package com.hopa.decision

/**
 * One vote from one layer, with what it cost.
 *
 * A failure is a vote too: a layer that throws must not disappear from the
 * comparison, or a broken adapter would look better than a working one.
 */
data class DecisionVote(
    val layerId: String,
    val layerVersion: String,
    val verb: Verb?,
    val reason: String,
    val confidence: Double,
    val trace: String,
    val latencyMicros: Long,
    val applied: Boolean,
    /** The candidate the layer chose, when it chose among several. */
    val chosen: String? = null,
    val error: String? = null,
)

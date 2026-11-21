package com.hopa.decision

import java.time.Duration

/** How long one decision may take before the loop should stop waiting for it. */
enum class LatencyClass(val budget: Duration) {
    /** A rule or a lookup: a few microseconds. */
    MICRO(Duration.ofMillis(5)),

    /** Anything that touches the disk or a small model: tens of milliseconds. */
    MILLI(Duration.ofMillis(250)),

    /** A network or a large model: seconds, never on the hot path of every step. */
    SECONDS(Duration.ofSeconds(5)),
}

/** Where a layer is in its life: written, observing, trusted, gone. */
enum class LayerStatus {
    /** Built and tested, never applied to real traffic. */
    EXPERIMENTAL,

    /** Registered, votes on real traffic, applies nothing. */
    SHADOW,

    /** May be chosen to apply by [DecisionMode.Automatic]. */
    DEFAULT,

    /** Removed from the build; a setting that still names it falls back. */
    RETIRED,
}

/** Whether the layer has passed the conformance kit. */
enum class ConformanceStatus {
    NOT_RUN,
    PASSED,
    FAILED,
}

/**
 * What a layer is, before it is asked anything.
 *
 * [decidesUpTo] is the strongest action class the layer may decide alone; a
 * layer that decides reads is not allowed to decide payments. The selector
 * enforces this, so a new adapter cannot quietly widen its own remit.
 */
data class LayerMetadata(
    val id: String,
    val version: String,
    val latency: LatencyClass,
    val decidesUpTo: ActionClass,
    val status: LayerStatus,
    val conformance: ConformanceStatus,
    val author: String,
    val summary: String,
) {
    init {
        require(ID.matches(id)) { "layer id must be lower-case words: $id" }
        require(version.isNotBlank()) { "layer $id has no version" }
        require(author.isNotBlank()) { "layer $id has no author" }
        require(summary.isNotBlank()) { "layer $id has no summary" }
    }

    /** True when a layer with this metadata may speak about [actionClass]. */
    fun covers(actionClass: ActionClass): Boolean = decidesUpTo.ordinal >= actionClass.ordinal

    private companion object {
        val ID = Regex("[a-z][a-z0-9]*(-[a-z0-9]+)*")
    }
}

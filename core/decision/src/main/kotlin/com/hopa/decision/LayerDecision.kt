package com.hopa.decision

/**
 * One answer from one layer.
 *
 * [reason] is a stable code (`payment_requires_approval`, not a sentence), so
 * the run log can be grouped and the comparison harness can count. [trace] is
 * one line for a person to read when something went wrong.
 */
data class LayerDecision(
    val verb: Verb,
    val reason: String,
    val confidence: Double,
    val trace: String,
    /** The candidate this decision chooses, for an ACT over several edges. */
    val chosen: String? = null,
) {
    init {
        require(ReasonCode.matches(reason)) { "reason must be a code, got: $reason" }
        require(confidence in 0.0..1.0) { "confidence must be in 0..1, got: $confidence" }
        require(trace.isNotBlank()) { "a decision needs a trace line" }
        require(chosen == null || chosen.isNotBlank()) { "a chosen candidate id cannot be blank" }
    }
}

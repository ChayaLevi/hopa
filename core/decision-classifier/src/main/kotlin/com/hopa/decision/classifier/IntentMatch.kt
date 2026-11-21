package com.hopa.decision.classifier

/** A recognized command and how sure the classifier is, from 0 to 1. */
data class IntentMatch(
    val intent: Intent,
    val confidence: Double,
) {
    init {
        require(confidence in 0.0..1.0) { "confidence must be in 0..1: $confidence" }
    }
}

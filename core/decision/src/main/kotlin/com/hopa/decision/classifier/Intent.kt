package com.hopa.decision.classifier

import com.hopa.contracts.wire.Action

/** One canonical command, already turned into the step it means. */
data class Intent(
    val id: String,
    val action: Action,
    /** What the person is told after the step ran. */
    val answer: String,
)

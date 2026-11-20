package com.hopa.inference.local

import kotlinx.serialization.Serializable

/** One step of a plan, as the loop parses it. */
@Serializable
data class PlanStep(
    val kind: String,
    val target: String,
    val reason: String = "",
)

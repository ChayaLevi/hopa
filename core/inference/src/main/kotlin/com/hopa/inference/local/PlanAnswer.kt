package com.hopa.inference.local

import kotlinx.serialization.Serializable

/** What a plan answer must be: an object with a non-empty list of steps. */
@Serializable
data class PlanAnswer(
    val steps: List<PlanStep>,
)

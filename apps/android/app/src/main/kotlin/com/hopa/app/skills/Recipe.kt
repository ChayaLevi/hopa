package com.hopa.app.skills

import com.hopa.core.contracts.Action

/** A goal and the actions that satisfied it once, so it can be replayed. */
data class Recipe(
    val goal: String,
    val steps: List<Action>,
    val at: Long,
)

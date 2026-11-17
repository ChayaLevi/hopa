package com.hopa.app.skills

import com.hopa.engine.contracts.Action

/** A goal and the actions that satisfied it once, so it can be replayed. */
data class Recipe(
    val goal: String,
    val steps: List<Action>,
    val at: Long,
) {
    /** The app this recipe opens first; empty when it works on whatever is on screen. */
    val app: String = steps.firstOrNull { it.kind == "ui.launch" }?.app.orEmpty()
}

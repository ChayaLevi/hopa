package com.hopa.app.recipes

import com.hopa.contracts.Observation

/**
 * What a replay managed to do. [done] steps ran; [complete] means every step of
 * the recipe ran **and** every ref was found again through its anchor. Anything
 * less is not the cheap path: the caller falls back to the model.
 *
 * [screen] is the tree the replay ended on, so the caller can answer the
 * person's question from it with one model call instead of saying "repeated".
 */
data class ReplayOutcome(
    val done: Int,
    val complete: Boolean,
    val screen: Observation? = null,
) {
    val trustworthy: Boolean get() = complete
}

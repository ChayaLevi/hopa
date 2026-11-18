package com.hopa.app.recipes

/**
 * What a replay managed to do. [done] steps ran; [complete] means every step of
 * the recipe ran **and** every ref was found again through its anchor. Anything
 * less is not the cheap path: the caller falls back to the model.
 *
 * The recipe also stores the hash of the screen it ended on, but a framework
 * window id changes between launches, so that hash is evidence rather than a
 * gate; the anchors are what a replay trusts.
 */
data class ReplayOutcome(
    val done: Int,
    val complete: Boolean,
) {
    val trustworthy: Boolean get() = complete
}

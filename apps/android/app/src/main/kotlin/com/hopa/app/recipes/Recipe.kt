package com.hopa.app.recipes

import com.hopa.contracts.Action

/**
 * A goal and the actions that satisfied it once, so it can be replayed.
 *
 * The two hashes are the honest guard around a replay: [screenHash] is the tree
 * the recipe started from, [endHash] the one it ended on. Positions in a tree
 * are not stable between runs, so replaying a tap by its old `ref` is only safe
 * when the screen is the same screen.
 */
/**
 * A step that acts on a `ref` depends on the tree it was recorded in. Replaying
 * it by position would tap whatever now sits at that position, so the replayer
 * refuses ref-based recipes until each step records what it pointed at.
 */
data class Recipe(
    val goal: String,
    val steps: List<Action>,
    val at: Long,
    val screenHash: String = "",
    val endHash: String = "",
) {
    /** The app this recipe opens first; empty when it works on whatever is on screen. */
    val app: String = steps.firstOrNull { it.kind == "ui.launch" }?.app.orEmpty()

    /**
     * A step that acts on a `ref` depends on the tree it was recorded in. Until
     * the recipe records what that ref pointed at, it must not be replayed.
     */
    val needsStableScreen: Boolean = steps.any { it.ref?.isNotBlank() == true }
}

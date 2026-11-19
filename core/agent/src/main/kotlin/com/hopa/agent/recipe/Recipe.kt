package com.hopa.agent.recipe

import com.hopa.contracts.wire.Action

/**
 * A goal, the actions that satisfied it once, and what each action acted on.
 *
 * A ref (`e21`) is a position in one tree; [anchors] holds the text/description/id
 * of the control each step touched, so a replay can find the same control after
 * the tree has been redrawn. A step without an anchor is positional and must not
 * be replayed.
 *
 * [screenHash] and [endHash] are the trees the run started and ended on. They are
 * kept as evidence; the anchors, not the hashes, are what a replay trusts,
 * because a framework window id changes between launches.
 */
data class Recipe(
    val goal: String,
    val steps: List<Action>,
    val at: Long,
    val anchors: List<String> = emptyList(),
    val screenHash: String = "",
    val endHash: String = "",
) {
    /** The app this recipe opens first; empty when it works on whatever is on screen. */
    val app: String = steps.firstOrNull { it.kind == "ui.launch" }?.app.orEmpty()

    fun anchorAt(index: Int): String = anchors.getOrElse(index) { "" }

    /** Every step either names no ref, or records what its ref pointed at. */
    val replayable: Boolean = steps.indices.all { index ->
        steps[index].ref.isNullOrBlank() || anchorAt(index).isNotBlank()
    }
}

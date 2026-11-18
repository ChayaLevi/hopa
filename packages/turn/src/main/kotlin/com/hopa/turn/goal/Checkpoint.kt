package com.hopa.turn.goal

/**
 * Something the person must be able to see when the turn is over.
 *
 * [anchor] is either a full [com.hopa.engine.observation.NodeAnchor] key
 * (`TextView|text=wi-fi|id=android:id/title`) or a visible label from the
 * screen (`Wi-Fi`). A label matches exactly, not as a substring: "Wi-Fi" must
 * not be satisfied by "Wi-Fi calling" on the wrong row.
 */
data class Checkpoint(
    val anchor: String,
    val label: String = anchor,
) {
    init {
        require(anchor.isNotBlank()) { "a checkpoint needs an anchor" }
    }
}

/** Which of the person's checkpoints the final screen shows. */
data class GoalVerification(
    val met: List<Checkpoint>,
    val missing: List<Checkpoint>,
) {
    val complete: Boolean get() = missing.isEmpty()
}

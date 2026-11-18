package com.hopa.app.recipes

/**
 * What a replay managed to do, so the caller can decide whether it may stop
 * here. [complete] means every step ran; [endMatched] means the screen ended
 * where the recorded run ended, which is the closest this format comes to
 * "the goal was met".
 */
data class ReplayOutcome(
    val done: Int,
    val complete: Boolean,
    val endMatched: Boolean,
) {
    /** The cheap path only counts when the whole recipe ran and the screen agrees. */
    val trustworthy: Boolean get() = complete && endMatched
}

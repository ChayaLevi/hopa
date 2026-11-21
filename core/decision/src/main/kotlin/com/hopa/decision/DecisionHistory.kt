package com.hopa.decision

/**
 * Markers a loop may append to [DecisionContext.history] so a rule can see what
 * happened without parsing narration.
 */
object DecisionHistory {
    /** The last action produced no visible change. */
    const val NO_CHANGE: String = "no visible change"

    /** The person refused the proposed action. */
    const val DECLINED: String = "user declined"
}

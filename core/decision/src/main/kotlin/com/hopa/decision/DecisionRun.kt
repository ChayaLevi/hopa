package com.hopa.decision

/** The votes of one step and the one that was applied, if any. */
data class DecisionRun(
    val applied: DecisionVote?,
    val votes: List<DecisionVote>,
    val notice: String? = null,
) {
    /** The applied vote's decision, when a layer was trusted with this step. */
    val decision: LayerDecision?
        get() = votes.firstOrNull { it.applied && it.error == null }?.let { vote ->
            vote.verb?.let { verb ->
                LayerDecision(
                    verb = verb,
                    reason = vote.reason,
                    confidence = vote.confidence,
                    trace = vote.trace,
                    chosen = vote.chosen,
                )
            }
        }

    companion object {
        val NONE: DecisionRun = DecisionRun(applied = null, votes = emptyList())
    }
}

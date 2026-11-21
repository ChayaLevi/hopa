package com.hopa.decision

import com.hopa.contracts.wire.Action

/**
 * Everything one decision needs, and nothing that only a model can use.
 *
 * The screen is a summary, not a rendered prompt: a layer that never calls a
 * model should not pay for a prompt. [runId] and [step] are here so a vote can
 * be written to the run log without the loop threading extra arguments.
 */
data class DecisionContext(
    val runId: String,
    val step: Int,
    val action: Action?,
    val screen: String = "",
    /** Signatures of the steps already taken, oldest first. */
    val history: List<String> = emptyList(),
    /** Signatures the person already allowed in this turn. */
    val approvals: List<String> = emptyList(),
    val attention: AttentionBudget = AttentionBudget(),
    /** The person pressed stop; no layer may act afterwards. */
    val cancelled: Boolean = false,
    /** The loop is out of steps and only a summary is still allowed. */
    val atStepLimit: Boolean = false,
) {
    val actionClass: ActionClass = ActionClass.of(action?.kindOrNull())

    val signature: String = ActionSignature.of(action)

    init {
        require(runId.isNotBlank()) { "a decision needs a run id" }
        require(step >= 0) { "step cannot be negative: $step" }
    }
}

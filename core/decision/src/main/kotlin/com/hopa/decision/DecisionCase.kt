package com.hopa.decision

import com.hopa.contracts.wire.Action

/**
 * One corpus situation with the decision a careful person would make.
 *
 * [allowed] is wider than [expected]: several verbs can be defensible, while
 * only one is the reference answer the harness scores against. [model] records
 * what the model-only path returned when the corpus was captured on a device,
 * so the report can carry a "no layer" row.
 */
data class DecisionCase(
    val id: String,
    val action: Action?,
    val screen: String = "",
    val history: List<String> = emptyList(),
    val approvals: List<String> = emptyList(),
    val attentionLeft: Int = 3,
    val cancelled: Boolean = false,
    val atStepLimit: Boolean = false,
    val expected: Verb,
    val allowed: List<Verb>,
    val model: Verb? = null,
    val notes: String = "",
) {
    fun context(): DecisionContext = DecisionContext(
        runId = "corpus",
        step = 0,
        action = action,
        screen = screen,
        history = history,
        approvals = approvals,
        attention = AttentionBudget(attentionLeft),
        cancelled = cancelled,
        atStepLimit = atStepLimit,
    )
}

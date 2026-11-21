package com.hopa.agent.decision

import com.hopa.contracts.wire.Action
import com.hopa.decision.ActionClass
import com.hopa.decision.AttentionBudget
import com.hopa.decision.DecisionContext
import com.hopa.decision.DecisionCoordinator
import com.hopa.decision.DecisionMode
import com.hopa.decision.DecisionRun
import com.hopa.decision.DecisionVoteSink

/**
 * The loop's one way to ask the decision layers.
 *
 * Mode and shadow are suppliers, not values: a setting changed while a turn is
 * running applies to its next step, and a test can flip it between calls. The
 * screen is cut to a summary here so a layer that never calls a model does not
 * receive a whole tree.
 */
class DecisionRuntime(
    private val coordinator: DecisionCoordinator,
    private val mode: () -> DecisionMode,
    private val shadow: () -> Boolean,
    private val sink: DecisionVoteSink? = null,
    /** True when this action class may follow the applied layer in this build. */
    private val applies: (ActionClass) -> Boolean = { false },
) {
    fun appliesTo(actionClass: ActionClass): Boolean = applies(actionClass)

    suspend fun observe(
        runId: String,
        step: Int,
        action: Action?,
        screen: String,
        history: List<String>,
        approvals: List<String> = emptyList(),
        cancelled: Boolean = false,
        atStepLimit: Boolean = false,
    ): DecisionRun {
        val context = DecisionContext(
            runId = runId,
            step = step,
            action = action,
            screen = screen.take(MAX_SCREEN_CHARS),
            history = history,
            approvals = approvals,
            attention = AttentionBudget(),
            cancelled = cancelled,
            atStepLimit = atStepLimit,
        )
        return coordinator.evaluate(context, mode(), shadow(), sink)
    }

    private companion object {
        const val MAX_SCREEN_CHARS = 800
    }
}

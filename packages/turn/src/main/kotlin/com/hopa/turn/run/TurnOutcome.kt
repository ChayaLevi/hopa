package com.hopa.turn.run

import com.hopa.contracts.HopaError
import com.hopa.contracts.Observation
import com.hopa.contracts.RunRecord
import com.hopa.contracts.model.ModelUsage
import com.hopa.turn.goal.GoalVerification

/** How the turn was answered, and therefore what the receipt can claim. */
enum class TurnSource {
    /** A stored recipe ran; no plan, no loop, at most one cheap model call. */
    RECIPE,

    /** The step loop ran, with the model choosing each step. */
    MODEL,

    /** The plan gate answered; nothing was tapped and the person decides next. */
    PLAN,

    /** No surface could see the screen, so the turn did not start. */
    UNAVAILABLE,
}

/**
 * What one turn produced. The caller turns this into bubbles, receipts and
 * storage; the runner never writes to a conversation and never posts a
 * notification.
 */
data class TurnOutcome(
    val source: TurnSource,
    val answer: String = "",
    val records: List<RunRecord> = emptyList(),
    val narration: List<String> = emptyList(),
    val options: List<String> = emptyList(),
    val error: HopaError? = null,
    val usage: ModelUsage = ModelUsage(),
    val surfaceName: String = "",
    val surfaceError: String = "",
    /** Steps the person is asked to approve; empty unless [source] is PLAN. */
    val pendingPlan: List<String> = emptyList(),
    /** Present only when the request carried checkpoints. */
    val checkpoints: GoalVerification? = null,
    /** The screen the turn ended on, when a step or replay observed one. */
    val finalScreen: Observation? = null,
    /** What the replay did, step by step; empty unless [source] is RECIPE. */
    val replaySteps: List<String> = emptyList(),
    /** True when this turn's steps were stored as a recipe for next time. */
    val recipeSaved: Boolean = false,
    val millis: Long = 0,
) {
    val executed: Int get() = records.count { it.executed }

    val verified: Int get() = records.count { it.verified }

    /** No error and no question: the turn finished on its own terms. */
    val clean: Boolean get() = error == null && options.isEmpty()
}

package com.hopa.decision

/**
 * What the selector picked for one action.
 *
 * [notice] is shown to the person when their setting could not be honoured
 * (the layer is gone from this build, or it may not decide this class). The
 * loop then uses [layer], or the model path when it is null.
 */
data class DecisionSelection(
    val layer: DecisionLayer?,
    val notice: String? = null,
    /** True whenever the action class keeps its human approval step. */
    val requiresApproval: Boolean = false,
)

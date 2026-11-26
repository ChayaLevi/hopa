package com.hopa.family.engine

import com.hopa.family.board.RejectCode

/** What [FamilyEngine.propose] did with a local change. */
sealed interface Proposed {
    /** Applied to this device's view at once and queued for the server. */
    data class Queued(val opId: String) : Proposed

    /** The board refuses it as it stands now (stale, not found, ...): nothing is queued. */
    data class Rejected(val code: RejectCode) : Proposed

    /** This device stopped at an op it cannot read (D15): no change until the app is updated. */
    data object UpdateRequired : Proposed

    /** This device does not hold the scope (a teen and `parents`, or no parents key yet). */
    data object ScopeNotHeld : Proposed
}

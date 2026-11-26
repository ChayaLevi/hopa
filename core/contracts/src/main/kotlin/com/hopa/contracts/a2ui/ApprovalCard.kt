package com.hopa.contracts.a2ui

/** What [ApprovalSurfaces.receive] made of a card sent with an approval request. */
sealed interface ApprovalCard {
    /** Every check passed: draw this surface. */
    data class Shown(val surface: A2uiSurface) : ApprovalCard

    /**
     * Not drawn; the answer is deny. [error] is the A2UI `error` message to
     * send back, or null when the card named no surface to blame.
     */
    data class Refused(val reason: String, val error: A2uiError?) : ApprovalCard
}

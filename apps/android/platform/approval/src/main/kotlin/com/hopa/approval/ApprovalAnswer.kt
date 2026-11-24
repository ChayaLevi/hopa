package com.hopa.approval

import com.hopa.contracts.a2ui.A2uiAction

/** What the person answered, and the A2UI tap that answered it when the sheet was a card. */
data class ApprovalAnswer(
    val allow: Boolean,
    val action: A2uiAction? = null,
) {
    companion object {
        val DENY = ApprovalAnswer(false)
    }
}

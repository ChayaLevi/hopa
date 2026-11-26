package com.hopa.approval

import com.hopa.contracts.a2ui.A2uiSurface
import com.hopa.contracts.approval.ApprovalRequest

/**
 * One question on screen: the typed [request], and the [card] another device
 * sent for it, already checked by `ApprovalSurfaces.receive`. A null card
 * means this device builds its own from the request.
 */
data class ApprovalQuestion(
    val request: ApprovalRequest,
    val card: A2uiSurface? = null,
)

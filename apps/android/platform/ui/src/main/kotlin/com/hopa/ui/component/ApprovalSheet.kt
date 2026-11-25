package com.hopa.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.hopa.contracts.a2ui.A2uiAction
import com.hopa.contracts.a2ui.A2uiCodec
import com.hopa.contracts.a2ui.A2uiOutcome
import com.hopa.contracts.a2ui.A2uiSession
import com.hopa.contracts.a2ui.A2uiSurface
import com.hopa.contracts.a2ui.ApprovalSurfaces
import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.ui.a2ui.A2uiApprovalSheet

/**
 * The approval question on this device. Since ADR-0031 it is an A2UI surface:
 * built by [ApprovalSurfaces] from the typed request, checked by
 * [A2uiSession] and HOPA's approval rules, drawn by [A2uiApprovalSheet].
 * A question from another device brings its own [card], which the caller has
 * already checked with `ApprovalSurfaces.receive`.
 * Allow is only an `approve` tap on the allow button of this surface, and
 * [onAllow] gets that tap; anything else, or a card that fails a check, is deny.
 */
@Composable
fun ApprovalSheet(
    request: ApprovalRequest,
    onDeny: () -> Unit,
    onAllow: (A2uiAction) -> Unit,
    card: A2uiSurface? = null,
) {
    val surface = remember(request, card) { card ?: localSurface(request) }
    if (surface == null) {
        LaunchedEffect(request) { onDeny() }
        return
    }
    A2uiApprovalSheet(surface) { action ->
        if (ApprovalSurfaces.allowed(allow = true, action = action, surfaceId = surface.id)) {
            onAllow(action)
        } else {
            onDeny()
        }
    }
}

private fun localSurface(request: ApprovalRequest): A2uiSurface? {
    val surfaceId = ApprovalSurfaces.surfaceId("local", 1)
    val message = ApprovalSurfaces.build(request, surfaceId).single()
    val surface = (A2uiSession().apply(A2uiCodec.encode(message)) as? A2uiOutcome.Ready)?.surface ?: return null
    return surface.takeIf { ApprovalSurfaces.problem(it, request.isPayment) == null }
}

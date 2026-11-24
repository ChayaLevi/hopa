package com.hopa.ui.a2ui

import com.hopa.contracts.a2ui.A2uiComponent
import com.hopa.contracts.a2ui.A2uiSurface
import com.hopa.contracts.a2ui.ApprovalSurfaces

/**
 * How an approval surface maps onto the sheet: the first Text is the title,
 * the Row of buttons becomes the dialog's two buttons, and everything between
 * is the body. Null when the surface does not have that shape, so the sheet
 * never guesses which button means yes.
 */
internal data class ApprovalLayout(
    val title: A2uiComponent.Text,
    val body: List<A2uiComponent>,
    val notice: A2uiComponent.PaymentNotice?,
    val approve: A2uiComponent.Button,
    val deny: A2uiComponent.Button,
) {
    companion object {
        fun of(surface: A2uiSurface): ApprovalLayout? {
            val card = surface.root as? A2uiComponent.Card ?: return null
            val content = surface.component(card.child) as? A2uiComponent.Column ?: return null
            val children = surface.children(content)
            val title = children.firstOrNull() as? A2uiComponent.Text ?: return null
            val row = children.lastOrNull() as? A2uiComponent.Row ?: return null
            val buttons = surface.children(row).filterIsInstance<A2uiComponent.Button>()
            if (buttons.size != surface.children(row).size) return null
            val approve = buttons.singleOrNull { it.event.name == ApprovalSurfaces.APPROVE } ?: return null
            val deny = buttons.singleOrNull { it.event.name == ApprovalSurfaces.DENY } ?: return null
            if (buttons.size != 2) return null
            val body = children.drop(1).dropLast(1)
            return ApprovalLayout(
                title = title,
                body = body,
                notice = body.filterIsInstance<A2uiComponent.PaymentNotice>().singleOrNull(),
                approve = approve,
                deny = deny,
            )
        }
    }
}

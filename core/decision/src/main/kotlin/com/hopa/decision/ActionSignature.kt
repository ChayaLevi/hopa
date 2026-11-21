package com.hopa.decision

import com.hopa.contracts.wire.Action

/**
 * The stable name of an action within one turn.
 *
 * Two taps on the same control are the same signature even when the screen moved.
 * The loop uses the same shape for its repeat guard, and a layer compares a
 * proposed action with the steps already taken by comparing signatures.
 */
object ActionSignature {
    fun of(action: Action?): String {
        if (action == null) {
            return ""
        }
        val kind = action.kind.trim()
        if (kind.isEmpty()) {
            return ""
        }
        val coordinates = if (action.x == null && action.y == null) {
            ""
        } else {
            "${action.x ?: ""},${action.y ?: ""}"
        }
        return listOf(
            kind,
            action.ref.orEmpty(),
            action.text.orEmpty(),
            action.app.orEmpty(),
            coordinates,
        ).filter { it.isNotBlank() }.joinToString("|")
    }
}

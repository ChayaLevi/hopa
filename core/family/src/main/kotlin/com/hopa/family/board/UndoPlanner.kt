package com.hopa.family.board

import com.hopa.family.board.BoardRejection.Companion.reject
import com.hopa.family.model.ActivityKind
import com.hopa.family.model.Item
import com.hopa.family.model.ItemStatus

/**
 * Homemind's `planUndo`. Undo restores every item the target op touched, once, and only if
 * nothing changed them since: it is version-checked, never a blind restore (D8). The target is
 * named by its op id, which is its activity id (D10).
 */
internal object UndoPlanner {
    fun plan(view: ScopeView, targetOpId: String, actor: String): Draft {
        val original = view.activity.firstOrNull { it.id == targetOpId } ?: reject(RejectCode.NOT_FOUND)
        val undo = original.undo ?: reject(RejectCode.NOT_FOUND)
        val touched = undo.items.mapNotNull { entry -> entry.before ?: view.find(entry.itemId) }
        if (touched.isNotEmpty() && !view.canSee(actor)) reject(RejectCode.NOT_FOUND)
        if (original.actor != actor && !view.isParent(actor)) reject(RejectCode.FORBIDDEN)
        if (original.undone != null) reject(RejectCode.CONFLICT)

        val next = linkedMapOf<String, Item?>()
        for (entry in undo.items) {
            val current = view.find(entry.itemId)
            if ((current?.version ?: 0) != entry.afterVersion) reject(RejectCode.STALE)
            if (entry.before == null) {
                val child = view.items.any { it.parentEventId == entry.itemId && it.status != ItemStatus.CANCELLED }
                if (child) reject(RejectCode.CONFLICT)
                next[entry.itemId] = null
            } else {
                // The old content comes back under a new version, so screens holding the undone one see it moved.
                next[entry.itemId] = entry.before
            }
        }
        return Draft(next, ActivityKind.UNDO, original.itemId, "Undid a change", ref = original.id, undoes = original)
    }
}

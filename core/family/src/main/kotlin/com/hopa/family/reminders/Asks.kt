package com.hopa.family.reminders

import com.hopa.family.model.BoardState
import com.hopa.family.model.FamilyRole
import com.hopa.family.model.Importance
import com.hopa.family.model.Item
import com.hopa.family.model.ItemStatus
import com.hopa.family.model.Ownership
import com.hopa.family.model.Scope
import com.hopa.family.scope.ScopeAccess

/** Homemind's `askFor`, with visibility read as the item's scope (D11). */
internal class Asks(private val state: BoardState, private val scope: Scope) {
    private fun visible(ids: List<String>): List<String> = ids.distinct().filter { id ->
        state.family.members.firstOrNull { it.id == id }?.let { ScopeAccess.mayWrite(it, scope) } == true
    }

    private fun parentIds(): List<String> =
        state.family.members.filter { it.role == FamilyRole.PARENT && it.hasAccount }.map { it.id }

    fun askFor(item: Item): Ask? {
        if (item.status != ItemStatus.OPEN || item.start == null || item.allDay == true) return null
        val ask = when (val owned = item.ownership) {
            is Ownership.Assigned ->
                Ask(if (owned.confirmed) ReminderAsk.HEADS_UP else ReminderAsk.CONFIRM, visible(listOf(owned.ownerId)))
            is Ownership.Open -> {
                val candidates = visible(owned.candidateIds)
                Ask(ReminderAsk.OPEN, candidates.ifEmpty { visible(parentIds()) })
            }
            Ownership.None -> {
                // Nobody owns it and nobody was asked: only a critical item (a child's pickup) is worth it.
                if (item.importance != Importance.CRITICAL) return null
                Ask(ReminderAsk.OPEN, visible(ScopeAccess.eligibleOwners(state.family, scope)))
            }
        }
        return ask.takeIf { it.recipients.isNotEmpty() }
    }
}

package com.hopa.family.scope

import com.hopa.family.model.BoardOp
import com.hopa.family.model.BoardState
import com.hopa.family.model.ItemStatus
import com.hopa.family.model.ItemType
import com.hopa.family.model.NewItem
import com.hopa.family.model.Scope

/**
 * D11: a parent device may tell the family that someone is unavailable because of a parents-only
 * event, without telling why. It writes a new `family` event with the same times and the same
 * unavailable members, a neutral title, and nothing else: no notes, no one it is for, no source
 * that names the original. The family can then plan around it (an open may cite it as the
 * reason), and a teen device never learns what the parents event is.
 *
 * The two events are not linked (a link would be a reference across scopes). If the parents event
 * moves, the device writes a fresh derivation and cancels the old one.
 */
object BusyDerivation {
    const val DEFAULT_TITLE = "Busy"

    /** The family op, or null when [eventId] is not a parents event that makes someone unavailable. */
    @Suppress("LongParameterList", "ReturnCount")
    fun derive(
        state: BoardState,
        actorId: String,
        eventId: String,
        newId: String,
        stamp: OpStamp,
        title: String = DEFAULT_TITLE,
    ): ScopedOp? {
        val actor = state.family.members.firstOrNull { it.id == actorId } ?: return null
        if (!ScopeAccess.mayWrite(actor, Scope.PARENTS)) return null
        val event = state.partition(Scope.PARENTS)?.items?.firstOrNull { it.id == eventId } ?: return null
        if (event.type != ItemType.EVENT || event.status == ItemStatus.CANCELLED) return null
        if (event.unavailableMemberIds.isEmpty() || event.start == null || event.end == null) return null
        return ScopedOp(
            Scope.FAMILY,
            BoardOp.Create(
                v = BoardOp.VERSION,
                opId = stamp.opId,
                at = stamp.at,
                item = NewItem(
                    id = newId,
                    type = ItemType.EVENT,
                    title = title,
                    start = event.start,
                    end = event.end,
                    allDay = event.allDay,
                    unavailableMemberIds = event.unavailableMemberIds,
                ),
            ),
        )
    }
}

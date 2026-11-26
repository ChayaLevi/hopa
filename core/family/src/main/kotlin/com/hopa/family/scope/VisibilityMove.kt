package com.hopa.family.scope

import com.hopa.family.model.BoardOp
import com.hopa.family.model.BoardState
import com.hopa.family.model.Importance
import com.hopa.family.model.Item
import com.hopa.family.model.ItemStatus
import com.hopa.family.model.ItemType
import com.hopa.family.model.NewItem
import com.hopa.family.model.Ownership
import com.hopa.family.model.OwnershipInput
import com.hopa.family.model.Scope

/**
 * Changing who sees an item (D11): a cancel in the scope it leaves (the tombstone), then a create
 * of a copy, with new ids, in the scope it joins. An event takes its open preparations along; the
 * single cancel of the event already cancels them in the old scope.
 *
 * What the copy keeps: type, title, notes, times, the all-day flag, who it is for, sensitivity,
 * importance, unavailability, repetition with its cancelled dates, the source and whether it is a
 * proposal. What it loses: the Chinese title (seeded text, as an edit of the title would), the
 * history (it is a new item), and an open reason (it names an event in the old scope). An owner
 * who cannot own items in the new scope (a teen, for `parents`) turns into "Who is taking this?",
 * and candidates are cut to those who may own items there.
 *
 * The reducer applies these ops like any others, so every device reaches the same result; the
 * move cases of `board-visibility.json` are the ops this helper writes.
 */
object VisibilityMove {
    @Suppress("LongParameterList", "ReturnCount")
    fun plan(
        state: BoardState,
        actorId: String,
        itemId: String,
        from: Scope,
        to: Scope,
        ids: IdMint,
        stamps: OpStamps,
    ): MovePlan {
        val actor = state.family.members.firstOrNull { it.id == actorId }
        if (actor == null || from == to || !ScopeAccess.mayWrite(actor, from) || !ScopeAccess.mayWrite(actor, to)) {
            return MovePlan.Refused(MoveRefusal.NOT_ALLOWED)
        }
        if (state.partition(to) == null) return MovePlan.Refused(MoveRefusal.NOT_ALLOWED)
        val source = state.partition(from) ?: return MovePlan.Refused(MoveRefusal.NOT_FOUND)
        val item = source.items.firstOrNull { it.id == itemId } ?: return MovePlan.Refused(MoveRefusal.NOT_FOUND)
        if (item.status == ItemStatus.DONE || item.status == ItemStatus.CANCELLED) {
            return MovePlan.Refused(MoveRefusal.CLOSED)
        }
        if (item.type == ItemType.PREPARATION) return MovePlan.Refused(MoveRefusal.PREPARATION)

        val eligible = ScopeAccess.eligibleOwners(state.family, to)
        val preparations = source.items.filter {
            it.type == ItemType.PREPARATION && it.parentEventId == item.id &&
                (it.status == ItemStatus.OPEN || it.status == ItemStatus.PROPOSED)
        }
        val ops = mutableListOf<ScopedOp>()
        val tombstone = stamps.next()
        ops += ScopedOp(from, BoardOp.Cancel(BoardOp.VERSION, tombstone.opId, tombstone.at, item.id, item.version))
        val newEventId = ids.idFor(item.id)
        ops += copyOf(item, newEventId, parentEventId = null, eligible, to, stamps)
        for (preparation in preparations) {
            ops += copyOf(preparation, ids.idFor(preparation.id), parentEventId = newEventId, eligible, to, stamps)
        }
        return MovePlan.Ops(ops)
    }

    /**
     * The ops that undo a move, newest first, each in the scope of the op it undoes. Each undo is
     * version-checked by the reducer (D8): if someone changed the copy meanwhile, the undo of that
     * op is refused as stale, and the engine should not send the rest.
     */
    fun undo(move: List<ScopedOp>, stamps: OpStamps): List<ScopedOp> = move.asReversed().map { done ->
        val stamp = stamps.next()
        ScopedOp(done.scope, BoardOp.Undo(BoardOp.VERSION, stamp.opId, stamp.at, targetOpId = done.op.opId))
    }

    @Suppress("LongParameterList")
    private fun copyOf(
        item: Item,
        newId: String,
        parentEventId: String?,
        eligible: List<String>,
        to: Scope,
        stamps: OpStamps,
    ): List<ScopedOp> {
        val stamp = stamps.next()
        val proposed = item.status == ItemStatus.PROPOSED
        val create = BoardOp.Create(
            v = BoardOp.VERSION,
            opId = stamp.opId,
            at = stamp.at,
            item = NewItem(
                id = newId,
                type = item.type,
                title = item.title,
                notes = item.notes,
                start = item.start,
                end = item.end,
                allDay = item.allDay,
                forMemberIds = item.forMemberIds.takeIf { it.isNotEmpty() },
                sensitive = true.takeIf { item.sensitive },
                importance = Importance.CRITICAL.takeIf { item.importance == Importance.CRITICAL },
                unavailableMemberIds = item.unavailableMemberIds.takeIf { it.isNotEmpty() },
                recurrence = item.recurrence,
                parentEventId = parentEventId,
                ownership = ownershipIn(item.ownership, eligible),
                source = item.source,
                proposed = true.takeIf { proposed && !item.source.kind.proposes },
            ),
        )
        val ops = mutableListOf(ScopedOp(to, create))
        // A source that proposes (group, email, ...) makes every create a proposal. The item was
        // approved already, so the copy is approved too, in the next op.
        if (!proposed && item.source.kind.proposes) {
            val next = stamps.next()
            ops += ScopedOp(to, BoardOp.Approve(BoardOp.VERSION, next.opId, next.at, newId, version = 1))
        }
        return ops
    }

    private fun ownershipIn(ownership: Ownership, eligible: List<String>): OwnershipInput? = when (ownership) {
        Ownership.None -> null
        is Ownership.Assigned ->
            if (ownership.ownerId in eligible) OwnershipInput.Assigned(ownership.ownerId) else OwnershipInput.Open()
        is Ownership.Open -> {
            val candidates = ownership.candidateIds.filter { it in eligible }
            OwnershipInput.Open(candidateIds = candidates.takeIf { it.isNotEmpty() })
        }
    }
}

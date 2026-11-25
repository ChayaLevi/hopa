package com.hopa.family.board

import com.hopa.family.board.BoardRejection.Companion.reject
import com.hopa.family.model.ActivityKind
import com.hopa.family.model.BoardOp
import com.hopa.family.model.ItemStatus
import com.hopa.family.model.ItemType
import com.hopa.family.model.Ownership
import com.hopa.family.model.OwnershipInput

/**
 * Homemind's `planItemChange`: an op on one existing item at the version the proposer saw.
 * First writer wins: a stale version is refused on every device (D8).
 */
internal object ItemChangePlanner {
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun plan(view: ScopeView, op: BoardOp.OnItem, actor: String, clearsRecurrence: Boolean): Draft {
        val item = view.find(op.itemId)
        if (item == null || !view.canSee(actor)) reject(RejectCode.NOT_FOUND)
        if (op.version != item.version) reject(RejectCode.STALE)
        if (item.status == ItemStatus.CANCELLED) reject(RejectCode.CONFLICT)

        val ownership = item.ownership
        val owner = (ownership as? Ownership.Assigned)?.ownerId
        val settled = item.status == ItemStatus.DONE
        fun notWhileDone() {
            if (settled) reject(RejectCode.CONFLICT)
        }

        return when (op) {
            is BoardOp.Edit -> {
                val patch = op.patch
                val next = view.withFields(
                    item,
                    Fields(
                        title = patch.title,
                        notes = patch.notes,
                        start = patch.start,
                        end = patch.end,
                        allDay = patch.allDay,
                        forMemberIds = patch.forMemberIds,
                        sensitive = patch.sensitive,
                        importance = patch.importance,
                        unavailableMemberIds = patch.unavailableMemberIds,
                        recurrence = patch.recurrence,
                        clearRecurrence = clearsRecurrence,
                    ),
                )
                Draft.one(next, ActivityKind.EDIT, "Edited")
            }

            is BoardOp.Assign -> {
                notWhileDone()
                if (op.ownerId !in view.eligibleOwners()) reject(RejectCode.INVALID)
                if (owner == op.ownerId) reject(RejectCode.CONFLICT)
                val assigned = Ownership.Assigned(op.ownerId, confirmed = op.ownerId == actor)
                Draft.one(
                    item.copy(ownership = assigned),
                    ActivityKind.ASSIGN,
                    "Assigned to ${view.nameOf(op.ownerId)}",
                    memberId = op.ownerId,
                )
            }

            is BoardOp.Open -> {
                notWhileDone()
                val open = view.checkOwnership(actor, OwnershipInput.Open(op.candidateIds ?: emptyList(), op.reason))
                // The reason stays off the activity: screens read it from the ownership.
                Draft.one(item.copy(ownership = open), ActivityKind.OPEN, "Asked who is taking this")
            }

            is BoardOp.Unassign -> {
                notWhileDone()
                if (ownership == Ownership.None) reject(RejectCode.CONFLICT)
                Draft.one(item.copy(ownership = Ownership.None), ActivityKind.UNASSIGN, "Cleared the owner")
            }

            is BoardOp.Claim -> {
                notWhileDone()
                if (actor !in view.eligibleOwners()) reject(RejectCode.FORBIDDEN)
                if (owner != null && owner != actor) reject(RejectCode.CONFLICT)
                if (owner == actor && ownership.confirmed) reject(RejectCode.CONFLICT)
                val claimed = Ownership.Assigned(actor, confirmed = true)
                Draft.one(
                    item.copy(ownership = claimed),
                    ActivityKind.CLAIM,
                    "${view.nameOf(actor)} is taking this",
                    memberId = actor,
                )
            }

            is BoardOp.Confirm -> {
                notWhileDone()
                if (owner != actor) reject(RejectCode.FORBIDDEN)
                if (ownership.confirmed) reject(RejectCode.CONFLICT)
                Draft.one(
                    item.copy(ownership = Ownership.Assigned(actor, true)),
                    ActivityKind.CONFIRM,
                    "Confirmed",
                    memberId = actor,
                )
            }

            is BoardOp.Handback -> {
                notWhileDone()
                if (owner != actor) reject(RejectCode.FORBIDDEN)
                if ((op.note?.length ?: 0) > ScopeView.MAX_NOTE) reject(RejectCode.INVALID)
                val note = op.note?.trim()?.takeIf { it.isNotEmpty() }
                val open = Ownership.Open(
                    candidateIds = view.eligibleOwners().filter { it != actor },
                    handedBackBy = actor,
                    note = note,
                )
                Draft.one(
                    item.copy(ownership = open),
                    ActivityKind.HANDBACK,
                    "Handed back",
                    memberId = actor,
                    note = note,
                )
            }

            is BoardOp.Done -> {
                notWhileDone()
                if (item.status == ItemStatus.PROPOSED) reject(RejectCode.CONFLICT)
                if (item.recurrence != null) reject(RejectCode.INVALID)
                Draft.one(item.copy(status = ItemStatus.DONE), ActivityKind.DONE, "Marked done")
            }

            is BoardOp.Cancel -> {
                val draft = Draft.one(item.copy(status = ItemStatus.CANCELLED), ActivityKind.CANCEL, "Removed")
                if (item.type == ItemType.EVENT) {
                    for (prep in view.preparationsOf(
                        item.id,
                    )) draft.next[prep.id] = prep.copy(status = ItemStatus.CANCELLED)
                }
                draft
            }

            is BoardOp.CancelOccurrence -> {
                val recurrence = item.recurrence
                if (recurrence == null || item.start == null) reject(RejectCode.INVALID)
                if (!BoardTime.isLocalDate(op.date)) reject(RejectCode.INVALID)
                if (!view.isOccurrenceDate(item, op.date)) reject(RejectCode.INVALID)
                val exceptions = recurrence.exceptions.orEmpty()
                if (op.date in exceptions) reject(RejectCode.CONFLICT)
                if (exceptions.size >= ScopeView.MAX_EXCEPTIONS) reject(RejectCode.INVALID)
                val next = item.copy(recurrence = recurrence.copy(exceptions = (exceptions + op.date).sorted()))
                Draft.one(next, ActivityKind.CANCEL_OCCURRENCE, "Cancelled one occurrence", ref = op.date)
            }

            is BoardOp.Approve, is BoardOp.Dismiss -> {
                if (item.status != ItemStatus.PROPOSED) reject(RejectCode.CONFLICT)
                val approve = op is BoardOp.Approve
                val status = if (approve) ItemStatus.OPEN else ItemStatus.CANCELLED
                val draft = if (approve) {
                    Draft.one(item.copy(status = status), ActivityKind.APPROVE, "Approved")
                } else {
                    Draft.one(item.copy(status = status), ActivityKind.DISMISS, "Dismissed")
                }
                // One tap for an event and the preparations proposed with it; Undo restores them all.
                if (item.type == ItemType.EVENT) {
                    for (prep in view.preparationsOf(item.id)) {
                        if (prep.status == ItemStatus.PROPOSED) draft.next[prep.id] = prep.copy(status = status)
                    }
                }
                draft
            }

            is BoardOp.Split -> error("a split is planned by CreatePlanner")
        }
    }
}

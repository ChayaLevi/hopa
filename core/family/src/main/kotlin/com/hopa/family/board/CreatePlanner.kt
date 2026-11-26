package com.hopa.family.board

import com.hopa.family.board.BoardRejection.Companion.reject
import com.hopa.family.model.ActivityKind
import com.hopa.family.model.BoardOp
import com.hopa.family.model.Importance
import com.hopa.family.model.Item
import com.hopa.family.model.ItemStatus
import com.hopa.family.model.ItemType
import com.hopa.family.model.NewItem
import com.hopa.family.model.Ownership
import com.hopa.family.model.OwnershipInput
import com.hopa.family.model.Scope
import com.hopa.family.model.Source
import com.hopa.family.model.SourceKind

/** Homemind's `planCreate` and `planSplit`, with every new id taken from the op (D10). */
internal object CreatePlanner {
    private val ID_PATTERN = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

    fun isId(value: String?): Boolean = value != null && ID_PATTERN.matches(value)

    fun create(view: ScopeView, input: NewItem, actor: String): Draft {
        val item = newItem(view, input, actor, checkId = true)
        val what = if (item.status == ItemStatus.PROPOSED) "Proposed an item" else "Added an item"
        return Draft(linkedMapOf(item.id to item), ActivityKind.CREATE, item.id, what)
    }

    /**
     * Builds the item a create adds. A split part skips the id checks here: the reducer checks
     * its ids after the split's own checks, so the codes match Homemind's, which minted them.
     */
    fun newItem(view: ScopeView, input: NewItem, actor: String, checkId: Boolean): Item {
        if (checkId && !isId(input.id)) reject(RejectCode.INVALID)
        if (checkId && view.find(input.id) != null) reject(RejectCode.CONFLICT)

        var parentEventId: String? = null
        if (input.type == ItemType.PREPARATION) {
            val parent = view.find(input.parentEventId)
            if (parent == null || !view.canSee(actor)) reject(RejectCode.NOT_FOUND)
            if (parent.type != ItemType.EVENT) reject(RejectCode.INVALID)
            if (parent.status == ItemStatus.CANCELLED) reject(RejectCode.CONFLICT)
            parentEventId = parent.id
        } else if (input.parentEventId != null) {
            reject(RejectCode.INVALID)
        }
        if (view.scope == Scope.PARENTS && !view.isParent(actor)) reject(RejectCode.FORBIDDEN)

        val source = input.source ?: Source(SourceKind.MANUAL, "Added by ${view.nameOf(actor)}")
        val proposed = input.proposed == true || source.kind.proposes
        val base = Item(
            id = input.id,
            type = input.type,
            title = "",
            forMemberIds = emptyList(),
            ownership = Ownership.None,
            sensitive = false,
            importance = Importance.NORMAL,
            unavailableMemberIds = emptyList(),
            parentEventId = parentEventId,
            status = if (proposed) ItemStatus.PROPOSED else ItemStatus.OPEN,
            source = source,
            createdBy = actor,
            version = 1,
        )
        val filled = view.withFields(
            base,
            Fields(
                title = input.title,
                notes = input.notes,
                start = input.start,
                end = input.end,
                allDay = input.allDay,
                forMemberIds = input.forMemberIds,
                sensitive = input.sensitive,
                importance = input.importance,
                unavailableMemberIds = input.unavailableMemberIds,
                recurrence = input.recurrence,
            ),
        )
        return filled.copy(ownership = view.checkOwnership(actor, input.ownership))
    }

    /**
     * One task or preparation that is really several: the first part replaces it, the others are
     * new items beside it, with the ids the op carries. One activity, so one Undo puts it back.
     */
    fun split(view: ScopeView, op: BoardOp.Split, actor: String): Draft {
        val item = view.find(op.itemId)
        if (item == null || !view.canSee(actor)) reject(RejectCode.NOT_FOUND)
        if (op.version != item.version) reject(RejectCode.STALE)
        if (item.status == ItemStatus.CANCELLED) reject(RejectCode.CONFLICT)
        if (item.status == ItemStatus.DONE) reject(RejectCode.CONFLICT)
        if (item.type == ItemType.EVENT) reject(RejectCode.INVALID)
        if (op.parts.size !in ScopeView.MIN_SPLIT_PARTS..ScopeView.MAX_SPLIT_PARTS) reject(RejectCode.INVALID)

        val first = op.parts.first()
        val edited = view.withFields(item, Fields(title = first.title, start = first.start?.takeIf { it.isNotEmpty() }))
        val next = linkedMapOf<String, Item?>(item.id to edited)
        // The new parts keep the owner; "Who's taking this?" stays open to the same people.
        val ownership: OwnershipInput? = when (val owned = item.ownership) {
            is Ownership.Assigned -> OwnershipInput.Assigned(owned.ownerId)
            is Ownership.Open -> OwnershipInput.Open(candidateIds = owned.candidateIds)
            Ownership.None -> null
        }
        var working = view
        for (part in op.parts.drop(1)) {
            val start = part.start ?: item.start
            val created = newItem(
                working,
                NewItem(
                    id = part.id ?: "",
                    type = item.type,
                    parentEventId = item.parentEventId,
                    title = part.title,
                    start = start?.takeIf { it.isNotEmpty() },
                    forMemberIds = item.forMemberIds,
                    sensitive = item.sensitive,
                    importance = item.importance,
                    ownership = ownership,
                    source = op.source ?: item.source,
                    proposed = if (item.status == ItemStatus.PROPOSED) true else null,
                ),
                actor,
                checkId = false,
            )
            next[created.id] = created
            working = ScopeView(view.family, view.scope, working.items + created, view.activity)
        }
        return Draft(next, ActivityKind.EDIT, item.id, "Split into ${op.parts.size} items")
    }
}

package com.hopa.family.board

import com.hopa.family.board.BoardRejection.Companion.reject
import com.hopa.family.model.Activity
import com.hopa.family.model.Family
import com.hopa.family.model.FamilyRole
import com.hopa.family.model.Item
import com.hopa.family.model.ItemStatus
import com.hopa.family.model.ItemType
import com.hopa.family.model.Member
import com.hopa.family.model.OpenReason
import com.hopa.family.model.Ownership
import com.hopa.family.model.OwnershipInput
import com.hopa.family.model.Recurrence
import com.hopa.family.model.Scope

/**
 * What one op sees: the roster and its own scope, nothing else (D11). The checks are Homemind's
 * (`core.ts`), with the item's visibility read as [scope].
 */
internal class ScopeView(
    val family: Family,
    val scope: Scope,
    val items: List<Item>,
    val activity: List<Activity>,
) {
    fun memberOf(id: String): Member? = family.members.firstOrNull { it.id == id }

    fun isParent(id: String): Boolean = memberOf(id)?.role == FamilyRole.PARENT

    fun nameOf(id: String): String = memberOf(id)?.name ?: id

    fun find(id: String?): Item? = id?.let { wanted -> items.firstOrNull { it.id == wanted } }

    /** Whether [memberId] may see items of this scope at all. */
    fun canSee(memberId: String): Boolean = scope == Scope.FAMILY || isParent(memberId)

    /** Members who could own an item here: everyone with an account; only parents in `parents`. */
    fun eligibleOwners(): List<String> =
        family.members.filter { it.hasAccount && (scope == Scope.FAMILY || it.role == FamilyRole.PARENT) }.map { it.id }

    fun preparationsOf(eventId: String): List<Item> = items.filter {
        it.type == ItemType.PREPARATION && it.parentEventId == eventId && it.status != ItemStatus.CANCELLED
    }

    fun checkMembers(ids: List<String>): List<String> {
        if (ids.size > MAX_MEMBERS) reject(RejectCode.INVALID)
        if (ids.any { memberOf(it) == null }) reject(RejectCode.INVALID)
        return ids.distinct()
    }

    /** Applies [fields] over [base] and checks the result (Homemind's `withFields`). */
    @Suppress("CyclomaticComplexMethod")
    fun withFields(base: Item, fields: Fields): Item {
        var next = base
        fields.title?.let { raw ->
            val title = raw.trim()
            if (title.isEmpty() || title.length > MAX_TITLE) reject(RejectCode.INVALID)
            // A seeded Chinese title would now describe something else.
            if (title != base.title) next = next.copy(title = title, titleZh = null)
        }
        fields.notes?.let { notes ->
            if (notes.length > MAX_NOTES) reject(RejectCode.INVALID)
            next = next.copy(notes = notes.takeIf { it.isNotBlank() })
        }
        fields.start?.let { next = next.copy(start = time(it)) }
        fields.end?.let { next = next.copy(end = time(it)) }
        fields.allDay?.let { next = next.copy(allDay = it) }
        fields.forMemberIds?.let { next = next.copy(forMemberIds = checkMembers(it)) }
        fields.unavailableMemberIds?.let { next = next.copy(unavailableMemberIds = checkMembers(it)) }
        fields.sensitive?.let { next = next.copy(sensitive = it) }
        fields.importance?.let { next = next.copy(importance = it) }
        if (fields.clearRecurrence) {
            next = next.copy(recurrence = null)
        } else {
            fields.recurrence?.let { next = next.copy(recurrence = checkRecurrence(it)) }
        }
        checkItem(next)
        return when (val ownership = next.ownership) {
            is Ownership.Open -> {
                val eligible = eligibleOwners()
                val candidates = ownership.candidateIds.filter { it in eligible }
                if (candidates == ownership.candidateIds) {
                    next
                } else {
                    next.copy(
                        ownership = ownership.copy(candidateIds = candidates),
                    )
                }
            }
            else -> next
        }
    }

    /** An empty value clears the time; anything else must be an ISO time in UTC. */
    private fun time(value: String): String? =
        if (value.isEmpty()) null else BoardTime.normalize(value) ?: reject(RejectCode.INVALID)

    private fun checkItem(item: Item) {
        val start = item.start
        val end = item.end
        if (end != null && start == null) reject(RejectCode.INVALID)
        if (start != null && end != null && BoardTime.millis(end) < BoardTime.millis(start)) reject(RejectCode.INVALID)
        if (item.recurrence != null && start == null) reject(RejectCode.INVALID)
        if (item.recurrence != null && item.type == ItemType.PREPARATION) reject(RejectCode.INVALID)
        if (item.unavailableMemberIds.isNotEmpty() && item.type != ItemType.EVENT) reject(RejectCode.INVALID)
        if (item.unavailableMemberIds.isNotEmpty() && (start == null || end == null)) reject(RejectCode.INVALID)
        val ownership = item.ownership
        if (ownership is Ownership.Assigned && ownership.ownerId !in eligibleOwners()) reject(RejectCode.INVALID)
    }

    private fun checkRecurrence(value: Recurrence): Recurrence {
        if (value.freq != "weekly") reject(RejectCode.INVALID)
        val days = value.byWeekday
        if (days.isEmpty() || days.size > MAX_WEEKDAYS) reject(RejectCode.INVALID)
        if (days.any { it !in 0..6 }) reject(RejectCode.INVALID)
        value.until?.let { if (!BoardTime.isLocalDate(it)) reject(RejectCode.INVALID) }
        val exceptions = value.exceptions.orEmpty()
        if (exceptions.size > MAX_EXCEPTIONS || exceptions.any { !BoardTime.isLocalDate(it) }) {
            reject(
                RejectCode.INVALID,
            )
        }
        return Recurrence(
            freq = "weekly",
            byWeekday = days.distinct().sorted(),
            until = value.until?.takeIf { it.isNotEmpty() },
            exceptions = exceptions.distinct().sorted().takeIf { it.isNotEmpty() },
        )
    }

    /** Homemind's `checkOwnership`: the ownership a create or an open asks for, checked. */
    fun checkOwnership(actor: String, input: OwnershipInput?): Ownership {
        val eligible = eligibleOwners()
        return when (input) {
            null, OwnershipInput.None -> Ownership.None
            is OwnershipInput.Open -> {
                val ids = input.candidateIds?.takeIf { it.isNotEmpty() }?.let(::checkMembers) ?: eligible
                if (!ids.all { it in eligible }) reject(RejectCode.INVALID)
                Ownership.Open(candidateIds = ids, reason = input.reason?.let(::checkReason))
            }
            is OwnershipInput.Assigned -> {
                if (input.ownerId !in eligible) reject(RejectCode.INVALID)
                Ownership.Assigned(ownerId = input.ownerId, confirmed = input.ownerId == actor)
            }
        }
    }

    /** A reason must name an event in this scope that makes that member unavailable. */
    private fun checkReason(reason: OpenReason): OpenReason {
        if (reason.kind != "unavailable") reject(RejectCode.INVALID)
        val event = find(reason.eventId)
        if (event == null || event.type != ItemType.EVENT || reason.memberId !in event.unavailableMemberIds) {
            reject(RejectCode.INVALID)
        }
        if (!BoardTime.isLocalDate(reason.date)) reject(RejectCode.INVALID)
        return OpenReason(kind = "unavailable", memberId = reason.memberId, eventId = event.id, date = reason.date)
    }

    /** Whether a weekly item has an occurrence on the local [date]. */
    fun isOccurrenceDate(item: Item, date: String): Boolean {
        val recurrence = item.recurrence ?: return false
        val start = item.start ?: return false
        if (date < BoardTime.localDate(start, family.timezone)) return false
        if (recurrence.until != null && date > recurrence.until) return false
        val weekday = BoardTime.weekday(date) ?: return false
        return weekday in recurrence.byWeekday
    }

    companion object {
        const val MAX_TITLE = 200
        const val MAX_NOTES = 2000
        const val MAX_NOTE = 500
        const val MAX_MEMBERS = 16
        const val MAX_WEEKDAYS = 7
        const val MAX_EXCEPTIONS = 400
        const val MIN_SPLIT_PARTS = 2
        const val MAX_SPLIT_PARTS = 4
    }
}

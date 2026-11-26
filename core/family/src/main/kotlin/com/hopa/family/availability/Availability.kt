package com.hopa.family.availability

import com.hopa.family.model.BoardState
import com.hopa.family.model.Item
import com.hopa.family.model.ItemStatus
import com.hopa.family.model.ItemType
import com.hopa.family.model.Ownership
import com.hopa.family.model.Partition
import com.hopa.family.model.Scope
import com.hopa.family.scope.MemberViews
import com.hopa.family.time.FamilyClock
import com.hopa.family.time.Occurrences

/**
 * Availability: an event's `unavailableMemberIds` against the items those members own at that time.
 * A port of Homemind's `availability.ts`, run per scope (D11): an event in `parents` never clashes
 * with a `family` item, because a family op could not name it as the reason. A parent device makes
 * the family plan around a parents event by deriving a busy event first (`BusyDerivation`).
 * `spec/fixtures/family/time-availability.json` is the contract. Pure.
 */
object Availability {
    private const val DAY = 24 * 60 * 60 * 1000L

    /** Events on the board that make someone unavailable (not proposed, done or removed). */
    private fun unavailabilityEvents(items: List<Item>): List<Item> = items.filter {
        it.type == ItemType.EVENT && it.status == ItemStatus.OPEN && it.unavailableMemberIds.isNotEmpty() &&
            it.start != null && it.end != null
    }

    private fun durationOf(item: Item): Long {
        val start = item.start ?: return 0
        val end = item.end ?: return 0
        return FamilyClock.millis(end) - FamilyClock.millis(start)
    }

    /** Half-open overlap. An item with no end counts as the instant it starts. */
    private fun overlaps(start: Long, end: Long?, otherStart: Long, otherEnd: Long): Boolean {
        if (end != null && end > start) return start < otherEnd && end > otherStart
        return start >= otherStart && start < otherEnd
    }

    /**
     * Conflicts in one partition whose clash is at least partly in `[fromMs, toMs)`. Unconfirmed
     * assignments count. All-day items, the event itself, its preparations, and the member's own
     * unavailability events are not conflicts. Sorted by time.
     */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    fun findConflicts(partition: Partition, zone: String, fromMs: Long, toMs: Long): List<Conflict> {
        val items = partition.items.filter {
            it.status == ItemStatus.OPEN && it.start != null && it.allDay != true && it.ownership is Ownership.Assigned
        }
        val out = mutableListOf<Conflict>()
        for (event in unavailabilityEvents(partition.items)) {
            for (eventOcc in Occurrences.of(event, fromMs - durationOf(event), toMs, zone)) {
                val eventStart = FamilyClock.millis(eventOcc.start)
                val eventEnd = FamilyClock.millis(requireNotNull(eventOcc.end))
                for (item in items) {
                    if (item.id == event.id || item.parentEventId == event.id) continue
                    val ownerId = (item.ownership as Ownership.Assigned).ownerId
                    if (ownerId !in event.unavailableMemberIds || ownerId in item.unavailableMemberIds) continue
                    for (occ in Occurrences.of(item, eventStart - durationOf(item) - 1, eventEnd, zone)) {
                        val start = FamilyClock.millis(occ.start)
                        val end = occ.end?.let { FamilyClock.millis(it) }
                        if (!overlaps(start, end, eventStart, eventEnd)) continue
                        val clashStart = maxOf(start, eventStart)
                        val clashEnd = minOf(if (end != null && end > start) end else start + 1, eventEnd)
                        if (clashEnd <= fromMs || clashStart >= toMs) continue
                        val recurring = item.recurrence != null
                        out += Conflict(ownerId, event.id, item.id, occ.date, occ.start, occ.end, recurring)
                    }
                }
            }
        }
        return out.sortedWith(compareBy({ it.start }, { it.itemId }, { it.eventId }))
    }

    /**
     * Who is unavailable when in `[fromMs, toMs)`, for [memberId]'s summary: only from the scopes
     * that member may read, so a parents event leaves no trace in a teen's summary.
     */
    fun unavailabilityFor(state: BoardState, memberId: String, fromMs: Long, toMs: Long): List<Unavailability> {
        val view = MemberViews.viewFor(state, memberId) ?: return emptyList()
        val out = mutableListOf<Unavailability>()
        val events = listOf(Scope.FAMILY, Scope.PARENTS).filter { it in view.scopes }
            .flatMap { unavailabilityEvents(requireNotNull(state.partition(it)).items) }
        for (event in events) {
            for (occ in Occurrences.of(event, fromMs - durationOf(event), toMs, state.family.timezone)) {
                val end = requireNotNull(occ.end)
                if (FamilyClock.millis(end) <= fromMs) continue
                event.unavailableMemberIds.forEach { out += Unavailability(it, event.id, occ.date, occ.start, end) }
            }
        }
        return out.sortedWith(compareBy({ it.start }, { it.memberId }))
    }

    /** Members unavailable at some point in `[startMs, endMs)` (an instant when [endMs] is null). */
    fun unavailableDuring(items: List<Item>, zone: String, startMs: Long, endMs: Long?): List<String> {
        val busy = sortedSetOf<String>()
        for (event in unavailabilityEvents(items)) {
            val window = Occurrences.of(event, startMs - durationOf(event) - DAY, (endMs ?: startMs) + DAY, zone)
            for (occ in window) {
                val occStart = FamilyClock.millis(occ.start)
                val occEnd = FamilyClock.millis(requireNotNull(occ.end))
                if (overlaps(startMs, endMs, occStart, occEnd)) busy += event.unavailableMemberIds
            }
        }
        return busy.toList()
    }

    /** [unavailableDuring] over every scope this device holds. */
    fun unavailableDuring(state: BoardState, startMs: Long, endMs: Long?): List<String> = unavailableDuring(
        listOfNotNull(state.scopes.family, state.scopes.parents).flatMap { it.items },
        state.family.timezone,
        startMs,
        endMs,
    )
}

package com.hopa.family.summary

import com.hopa.family.availability.Availability
import com.hopa.family.model.BoardState
import com.hopa.family.model.Importance
import com.hopa.family.model.ItemStatus
import com.hopa.family.model.ItemType
import com.hopa.family.model.Ownership
import com.hopa.family.reminders.ReminderTiming
import com.hopa.family.scope.MemberViews
import com.hopa.family.scope.ScopeAccess
import com.hopa.family.scope.ScopedItem
import com.hopa.family.time.FamilyClock
import com.hopa.family.time.Occurrences

/**
 * The evening summary and the week ahead (Homemind's `summary.ts`, `summaryFor`), built from one
 * member's own view, so a parents-only item or away event leaves no trace in a teen's summary.
 * Order: what you need to do, then who is away, then the events everyone should know about.
 *
 * Local to the device, not on the board: which reminders got no answer ([unanswered]), the group
 * messages that waited (the caller adds them for parents), and the start of the family's week
 * ([weekStartsOn], 0 = Sunday). `spec/fixtures/family/logic-summary.json` is the contract. Pure.
 */
object Summaries {
    private const val DAYS_IN_WEEK = 7L

    /** Whether the week ahead goes out on the evening of [date] (the next day starts the week). */
    fun weekAheadOn(date: String, weekStartsOn: Int): Boolean =
        FamilyClock.weekday(FamilyClock.addDays(date, 1)) == weekStartsOn

    @Suppress("LongParameterList", "CyclomaticComplexMethod")
    fun summaryFor(
        state: BoardState,
        memberId: String,
        kind: SummaryKind,
        nowMs: Long,
        weekStartsOn: Int,
        unanswered: Set<String> = emptySet(),
    ): SummaryView? {
        val view = MemberViews.viewFor(state, memberId) ?: return null
        val zone = state.family.timezone
        val date = FamilyClock.localParts(nowMs, zone).date
        var weeklyDate = date
        while (kind == SummaryKind.WEEKLY && !weekAheadOn(weeklyDate, weekStartsOn)) {
            weeklyDate = FamilyClock.addDays(weeklyDate, 1)
        }
        val evening = if (kind == SummaryKind.DAILY) date else weeklyDate
        val scheduledMs = FamilyClock.zonedToUtc(evening, ReminderTiming.SUMMARY_TIME, zone)

        // Daily: what is left of that day and the next. Weekly: the seven days after it.
        val from = if (kind == SummaryKind.DAILY) date else FamilyClock.addDays(date, 1)
        val to = if (kind == SummaryKind.DAILY) {
            FamilyClock.addDays(
                date,
                1,
            )
        } else {
            FamilyClock.addDays(date, DAYS_IN_WEEK)
        }
        val fromMs = FamilyClock.zonedToUtc(from, "00:00", zone)
        val toMs = FamilyClock.zonedToUtc(FamilyClock.addDays(to, 1), "00:00", zone)
        val items = view.items.filter { it.item.status == ItemStatus.OPEN }
        val asked = if (kind == SummaryKind.DAILY) unanswered else emptySet()

        val toDo = mutableListOf<SummaryToDo>()
        val listed = mutableSetOf<String>()
        for (scoped in items.sortedWith(compareBy({ it.item.start ?: "9" }, { it.item.id }))) {
            val item = scoped.item
            val why = if (item.id in asked) ToDoReason.UNANSWERED else toDoReason(state, scoped, memberId) ?: continue
            val start = item.start
            if (start == null) {
                // No date: a task to do some time. Listed, so it is not forgotten.
                toDo += SummaryToDo(item.id, why)
                listed += item.id
                continue
            }
            val first = Occurrences.of(item, fromMs, toMs, zone).firstOrNull()
            if (first != null || why == ToDoReason.UNANSWERED) {
                val on = first?.date ?: FamilyClock.localParts(FamilyClock.millis(start), zone).date
                toDo += SummaryToDo(item.id, why, on)
                listed += item.id
            }
        }

        val seen = mutableSetOf<String>()
        val away = Availability.unavailabilityFor(state, memberId, fromMs, toMs).filter {
            seen.add("${it.memberId}|${it.eventId}")
        }

        val everyone = items.mapNotNull { scoped ->
            val item = scoped.item
            if (item.type != ItemType.EVENT || item.id in listed || item.start == null) return@mapNotNull null
            Occurrences.of(item, fromMs, toMs, zone).firstOrNull()?.let { SummaryEvent(item.id, it.date) }
        }.sortedWith(compareBy({ it.date }, { it.itemId }))

        return SummaryView(
            kind = kind,
            date = if (kind == SummaryKind.DAILY) date else from,
            from = from,
            to = to,
            scheduledAt = FamilyClock.iso(scheduledMs),
            toDo = toDo,
            away = away,
            everyone = everyone,
        )
    }

    /** Homemind's `toDoReason`, with eligibility read from the item's scope. */
    private fun toDoReason(state: BoardState, scoped: ScopedItem, memberId: String): ToDoReason? {
        val item = scoped.item
        return when (val own = item.ownership) {
            is Ownership.Assigned -> when {
                own.ownerId != memberId -> null
                own.confirmed -> ToDoReason.OWNER
                else -> ToDoReason.UNCONFIRMED
            }
            is Ownership.Open -> ToDoReason.ASKED.takeIf { memberId in own.candidateIds }
            Ownership.None -> {
                // A task or preparation, or an important event, with nobody on it.
                val wantsOwner = item.type != ItemType.EVENT || item.importance == Importance.CRITICAL
                val eligible = ScopeAccess.eligibleOwners(state.family, scoped.scope)
                ToDoReason.ASKED.takeIf { wantsOwner && memberId in eligible }
            }
        }
    }
}

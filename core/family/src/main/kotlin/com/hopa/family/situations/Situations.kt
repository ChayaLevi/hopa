package com.hopa.family.situations

import com.hopa.family.availability.Availability
import com.hopa.family.model.BoardOp
import com.hopa.family.model.BoardState
import com.hopa.family.model.FamilyRole
import com.hopa.family.model.ItemPatch
import com.hopa.family.model.ItemStatus
import com.hopa.family.model.ItemType
import com.hopa.family.model.NewItem
import com.hopa.family.model.Ownership
import com.hopa.family.model.OwnershipInput
import com.hopa.family.model.Scope
import com.hopa.family.model.Source
import com.hopa.family.model.SourceKind
import com.hopa.family.scope.MemberViews
import com.hopa.family.scope.OpStamp
import com.hopa.family.scope.ScopeAccess
import com.hopa.family.scope.ScopedOp
import com.hopa.family.time.FamilyClock
import com.hopa.family.time.Occurrences

/**
 * A day gone wrong: "Noa is sick" (Homemind's `situations.ts`). The model only reads the message
 * (who, which day); everything after is fixed rules over the board, so it is the same every time:
 * what falls through that member's day (from the reporter's own view), which parents are free, and
 * the suggestions. Each suggestion, and the "stay home with Noa" task, is an ordinary board op in
 * the item's own scope, with its own Undo; nothing changes until a person taps.
 *
 * The cards to the teacher or a babysitter are actions outside the family: they go through
 * `ActionExecutor` on the acting device (D7), not through this module.
 * `spec/fixtures/family/logic-situations.json` is the contract. Pure.
 */
object Situations {
    /** How far ahead a report may be ("sick tomorrow" is fine; next month is not a situation). */
    const val MAX_DAYS_AHEAD = 6L

    /** Titles that mean taking a child to or from somewhere: not needed when the child stays home. */
    val PICKUP_PATTERN = Regex("pick[\\s-]?up|drop[\\s-]?off|接孩子|接送|送孩子|接娃|送娃|接诺娅|送诺娅", RegexOption.IGNORE_CASE)

    private const val DAYS_IN_WEEK = 7L

    /** Null when the report may start a situation now, else why not (Homemind's order of checks). */
    fun check(state: BoardState, reporterId: String, report: SituationReport, nowMs: Long): SituationRefusal? {
        val reporter = state.family.members.firstOrNull { it.id == reporterId }
        if (reporter == null || !reporter.hasAccount) return SituationRefusal.FORBIDDEN
        if (state.family.members.none { it.id == report.memberId }) return SituationRefusal.NOT_FOUND
        val today = FamilyClock.localParts(nowMs, state.family.timezone).date
        if (!Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(report.date) || report.date < today) return SituationRefusal.PAST
        if (report.date > FamilyClock.addDays(today, MAX_DAYS_AHEAD)) return SituationRefusal.TOO_FAR
        return null
    }

    /** What falls through that day and what to suggest, from [reporterId]'s view. Only what is still ahead. */
    fun plan(state: BoardState, reporterId: String, report: SituationReport, nowMs: Long): SituationPlan {
        val zone = state.family.timezone
        val dayStart = FamilyClock.zonedToUtc(report.date, "00:00", zone)
        val dayEnd = FamilyClock.zonedToUtc(FamilyClock.addDays(report.date, 1), "00:00", zone)
        val from = maxOf(dayStart, nowMs)
        val proposals = mutableListOf<SituationProposal>()
        val seen = mutableSetOf<String>()

        val live = MemberViews.viewFor(state, reporterId)?.items.orEmpty()
            .filter { it.item.status == ItemStatus.OPEN && it.item.start != null }
            .sortedWith(compareBy({ it.item.start }, { it.item.id }))
        for ((scope, item) in live) {
            val occurrence = Occurrences.of(item, from, dayEnd, zone).firstOrNull() ?: continue
            val start = requireNotNull(item.start)
            val attends = item.type == ItemType.EVENT && report.memberId in item.forMemberIds
            val owned = item.ownership as? Ownership.Assigned
            fun add(build: (String) -> SituationProposal) {
                if (seen.add(item.id)) proposals += build("p${proposals.size + 1}")
            }
            when {
                attends && item.recurrence != null -> add {
                    SituationProposal(it, ProposalKind.SKIP, scope, item.id, start = start, date = occurrence.date)
                }
                attends && PICKUP_PATTERN.containsMatchIn("${item.title} ${item.titleZh.orEmpty()}") -> add {
                    SituationProposal(it, ProposalKind.CANCEL, scope, item.id, start = start)
                }
                attends -> add {
                    SituationProposal(
                        it,
                        ProposalKind.POSTPONE,
                        scope,
                        item.id,
                        start = start,
                        newStart = shiftLocal(start, zone),
                        newEnd = item.end?.let { end -> shiftLocal(end, zone) },
                    )
                }
                owned != null && owned.ownerId == report.memberId -> {
                    val candidates = ScopeAccess.eligibleOwners(state.family, scope).filter { it != report.memberId }
                    if (candidates.isNotEmpty()) {
                        add {
                            SituationProposal(
                                it,
                                ProposalKind.OPEN,
                                scope,
                                item.id,
                                start = start,
                                candidateIds = candidates,
                            )
                        }
                    }
                }
            }
        }

        val parents = state.family.members
            .filter { it.role == FamilyRole.PARENT && it.hasAccount && it.id != report.memberId }
            .map { it.id }
        val away = Availability.unavailabilityFor(state, reporterId, from, dayEnd)
            .filter { it.memberId in parents }
            .map { Away(it.memberId, it.eventId, it.start, it.end) }
        val available = parents.filter { id -> away.none { it.memberId == id } }
        return SituationPlan(proposals, available, away, parents)
    }

    /**
     * A sick child needs someone at home: "Stay home with Noa", open to the free parents (or to all
     * of them when none is free). It never picks one. Proposed (to approve) when [mayWrite] is false
     * (autonomy level 1, or paused). Null for anyone but a child, or with no parent.
     */
    @Suppress("LongParameterList", "ReturnCount")
    fun stayHome(
        state: BoardState,
        reporterId: String,
        report: SituationReport,
        plan: SituationPlan,
        newId: String,
        stamp: OpStamp,
        mayWrite: Boolean,
    ): ScopedOp? {
        val sick = state.family.members.firstOrNull { it.id == report.memberId } ?: return null
        val reporter = state.family.members.firstOrNull { it.id == reporterId } ?: return null
        if (sick.role != FamilyRole.CHILD || plan.parents.isEmpty()) return null
        val item = NewItem(
            id = newId,
            type = ItemType.TASK,
            title = "Stay home with ${sick.name}",
            notes = "${sick.name} is sick (reported by ${reporter.name}).",
            start = FamilyClock.iso(FamilyClock.zonedToUtc(report.date, "00:00", state.family.timezone)),
            allDay = true,
            forMemberIds = listOf(sick.id),
            ownership = OwnershipInput.Open(candidateIds = plan.available.ifEmpty { plan.parents }),
            source = Source(SourceKind.CHAT, reporter.name),
            proposed = true.takeIf { !mayWrite },
        )
        return ScopedOp(Scope.FAMILY, BoardOp.Create(BoardOp.VERSION, stamp.opId, stamp.at, item))
    }

    /** The op for one suggestion against the item as it is now, or why it no longer fits. */
    fun opFor(state: BoardState, proposal: SituationProposal, stamp: OpStamp): Result {
        val item = state.partition(proposal.scope)?.items?.firstOrNull { it.id == proposal.itemId }
        if (item == null || item.status != ItemStatus.OPEN) return Result.Refused(SituationRefusal.GONE)
        if (proposal.start != null && item.start != proposal.start) return Result.Refused(SituationRefusal.CHANGED)
        val v = BoardOp.VERSION
        val op: BoardOp = when (proposal.kind) {
            ProposalKind.CANCEL -> BoardOp.Cancel(v, stamp.opId, stamp.at, item.id, item.version)
            ProposalKind.SKIP -> BoardOp.CancelOccurrence(
                v,
                stamp.opId,
                stamp.at,
                item.id,
                item.version,
                requireNotNull(proposal.date),
            )
            ProposalKind.POSTPONE -> BoardOp.Edit(
                v,
                stamp.opId,
                stamp.at,
                item.id,
                item.version,
                ItemPatch(start = proposal.newStart, end = proposal.newEnd),
            )
            ProposalKind.OPEN -> {
                if (item.ownership is Ownership.Open) return Result.Refused(SituationRefusal.CHANGED)
                BoardOp.Open(
                    v,
                    stamp.opId,
                    stamp.at,
                    item.id,
                    item.version,
                    proposal.candidateIds?.takeIf { it.isNotEmpty() },
                )
            }
        }
        return Result.Op(ScopedOp(proposal.scope, op))
    }

    /** The same local time a week later (stays at 16:30 across a daylight-saving change). */
    private fun shiftLocal(iso: String, zone: String): String {
        val local = FamilyClock.localParts(FamilyClock.millis(iso), zone)
        return FamilyClock.iso(FamilyClock.zonedToUtc(FamilyClock.addDays(local.date, DAYS_IN_WEEK), local.time, zone))
    }

    sealed interface Result {
        data class Op(val op: ScopedOp) : Result

        data class Refused(val reason: SituationRefusal) : Result
    }
}

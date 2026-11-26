package com.hopa.family.availability

import com.hopa.family.board.BoardJson
import com.hopa.family.board.BoardReducer
import com.hopa.family.board.BoardStep
import com.hopa.family.board.RejectCode
import com.hopa.family.model.BoardOp
import com.hopa.family.model.BoardState
import com.hopa.family.model.LogEntry
import com.hopa.family.model.NewItem
import com.hopa.family.model.OpenReason
import com.hopa.family.model.OwnershipInput
import com.hopa.family.model.Scope
import com.hopa.family.model.Source
import com.hopa.family.model.SourceKind
import com.hopa.family.scope.IdMint
import com.hopa.family.scope.OpStamps
import com.hopa.family.scope.ScopeAccess
import com.hopa.family.scope.ScopedOp
import com.hopa.family.time.FamilyClock
import kotlinx.serialization.json.JsonObject

/**
 * Opens each conflicting item for someone else to take (Homemind's `proposeForConflicts`). It never
 * assigns anyone: the family picks. Candidates are those who could own it and are free then; if
 * nobody is, everyone else who could own it. For a weekly item only that occurrence moves: it is
 * cancelled in the series and a one-off copy is opened, with the id `<itemId>-<date>` when free
 * (else one from [IdMint]). Conflicts on the same item and date are handled once.
 *
 * The ops are checked against the reducer as they are planned, so the versions are right and an op
 * the board would refuse is never sent: all or nothing.
 */
object ConflictProposals {
    private const val MAX_ID = 64
    private val AVAILABILITY = Source(SourceKind.RULE, "Availability check")

    @Suppress("LongParameterList", "ReturnCount")
    fun propose(
        state: BoardState,
        scope: Scope,
        conflicts: List<Conflict>,
        actorId: String,
        ids: IdMint,
        stamps: OpStamps,
    ): Proposal {
        var current = state
        val ops = mutableListOf<ScopedOp>()
        val opened = mutableListOf<String>()
        val handled = mutableSetOf<String>()
        fun write(op: BoardOp): RejectCode? {
            val entry = LogEntry(
                seq = ops.size + 1L,
                scope = scope,
                memberId = actorId,
                fromDevice = "planning",
                op = BoardJson.json.encodeToJsonElement(BoardOp.serializer(), op) as JsonObject,
            )
            return when (val step = BoardReducer.apply(current, entry)) {
                is BoardStep.Applied -> {
                    current = step.state
                    ops += ScopedOp(scope, op)
                    null
                }
                is BoardStep.Rejected -> step.code
                BoardStep.Stopped -> RejectCode.INVALID
            }
        }

        for (conflict in conflicts) {
            if (!handled.add("${conflict.itemId}|${conflict.date}")) continue
            val partition = current.partition(scope) ?: return Proposal.Refused(RejectCode.NOT_FOUND)
            val item = partition.items.firstOrNull { it.id == conflict.itemId } ?: continue
            val reason = OpenReason("unavailable", conflict.memberId, conflict.eventId, conflict.date)
            val candidates = candidatesFor(current, scope, conflict)
            if (!conflict.recurring) {
                val stamp = stamps.next()
                write(BoardOp.Open(BoardOp.VERSION, stamp.opId, stamp.at, item.id, item.version, candidates, reason))
                    ?.let { return Proposal.Refused(it) }
                opened += item.id
                continue
            }
            val cancel = stamps.next()
            write(
                BoardOp.CancelOccurrence(BoardOp.VERSION, cancel.opId, cancel.at, item.id, item.version, conflict.date),
            )
                ?.let { return Proposal.Refused(it) }
            val wanted = "${item.id}-${conflict.date}"
            val taken = current.partition(scope)?.items.orEmpty().any { it.id == wanted }
            val copyId = if (wanted.length <= MAX_ID && !taken) wanted else ids.idFor(wanted)
            val create = stamps.next()
            val copy = NewItem(
                id = copyId,
                type = item.type,
                title = item.title,
                notes = item.notes,
                start = conflict.start,
                end = conflict.end,
                forMemberIds = item.forMemberIds,
                sensitive = item.sensitive,
                importance = item.importance,
                // Not the original source: a group source would make the copy wait for approval.
                source = AVAILABILITY,
                ownership = OwnershipInput.Open(candidateIds = candidates, reason = reason),
            )
            write(BoardOp.Create(BoardOp.VERSION, create.opId, create.at, copy))?.let { return Proposal.Refused(it) }
            opened += copyId
        }
        return Proposal.Ops(ops, opened)
    }

    private fun candidatesFor(state: BoardState, scope: Scope, conflict: Conflict): List<String>? {
        val others = ScopeAccess.eligibleOwners(state.family, scope).filter { it != conflict.memberId }
        val partition = requireNotNull(state.partition(scope))
        val busy = Availability.unavailableDuring(
            partition.items,
            state.family.timezone,
            FamilyClock.millis(conflict.start),
            conflict.end?.let { FamilyClock.millis(it) },
        )
        val free = others.filter { it !in busy }
        // Null lets the reducer offer it to everyone who could own it.
        return free.ifEmpty { others }.ifEmpty { null }
    }
}

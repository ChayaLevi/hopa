package com.hopa.family.engine

import com.hopa.family.board.BoardJson
import com.hopa.family.board.Outcome
import com.hopa.family.board.RejectCode
import com.hopa.family.model.BoardOp
import com.hopa.family.model.BoardState
import com.hopa.family.model.Family
import com.hopa.family.model.LogEntry
import com.hopa.family.model.Partition
import com.hopa.family.model.Scope
import com.hopa.family.model.Scopes
import com.hopa.family.namespace.FamilyReducer
import com.hopa.family.namespace.FamilyStep
import com.hopa.family.namespace.Namespaces
import com.hopa.family.reminders.DueAlerts
import com.hopa.family.reminders.LocalSnooze
import com.hopa.family.reminders.PlannedAlert
import com.hopa.family.reminders.ReminderPlanner
import com.hopa.family.reminders.ReminderSettings
import com.hopa.family.reminders.ReminderTiming
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * One device's family board: the log the server ordered, replayed through the reducers
 * ([confirmed]), plus this device's own proposals on top ([view], what the screens show). Pure and
 * single-threaded: the app calls it from one place, and moves ops in and out through the board link.
 *
 *  - [propose] checks a change against the view, applies it there at once and queues it (outbox);
 *  - [receive] applies log entries in seq order per scope, skips what it already has, recognises its
 *    own ops when they come back, and rebases what is still pending on top: a pending op the board
 *    now refuses is dropped and reported (first writer wins, D8);
 *  - amended D15: an op of a capability this build does not know is kept aside and the rest goes on;
 *    an unknown kind or a newer version of a known capability stops that capability and those that
 *    read it; an envelope version this build does not know stops everything. Once the app is
 *    updated, [needsRebuild] says so and [rebuilt] starts over from the log;
 *  - [alerts] plans this member's reminders from the view (D5);
 *  - [snapshot] is what the device stores ([EngineStore] versions it).
 *
 * The actor of a local proposal is this device's member, as the server will attest it (D12).
 */
class FamilyEngine(
    start: EngineSnapshot,
    private val namespaces: Namespaces = Namespaces.DEFAULT,
) {
    private var confirmed: BoardState = start.confirmed
    private val applied: MutableMap<Scope, Long> = start.applied.toMutableMap()
    private val outbox: MutableList<PendingOp> = start.outbox.toMutableList()
    private var stoppedAt: Long? = start.stoppedAt
    private val stoppedNamespaces: MutableMap<String, Long> = start.stoppedNamespaces.toMutableMap()
    private val held: MutableList<LogEntry> = start.held.toMutableList()
    private val memberId = start.memberId
    private val deviceId = start.deviceId

    /** The board as this device shows it: the log, then its own pending proposals. */
    var view: BoardState = confirmed
        private set

    init {
        rebase(mutableListOf())
    }

    /** Some capability (or everything) waits for an app update. */
    val updateRequired: Boolean get() = stoppedAt != null || stoppedNamespaces.isNotEmpty()

    /** Capabilities stopped at an op this build cannot read; with [stoppedAt] set, all of them. */
    val stopped: Set<String>
        get() = stoppedNamespaces.keys + stoppedNamespaces.keys.flatMap { namespaces.readersOf(it) }

    /** Capabilities other devices use that this build does not know; their ops are kept aside. */
    val keptAside: Set<String>
        get() = held.mapNotNull { FamilyReducer.namespaceOf(it.op) }.filter { !namespaces.knows(it) }.toSet()

    /**
     * True when this build can now read an entry it kept aside or stopped at (the app was updated).
     * The app then replaces this engine with [rebuilt] and pulls the log again from the start, so
     * every capability is replayed in seq order against the board as it was then.
     */
    val needsRebuild: Boolean get() = held.any { FamilyReducer.readable(it.op, namespaces) }

    /** The scopes this device holds. */
    val scopes: Set<Scope> get() = Scope.entries.filter { confirmed.partition(it) != null }.toSet()

    /** What to send, oldest first. Sending one twice is safe: the server answers a duplicate. */
    fun outbox(): List<PendingOp> = outbox.toList()

    /** The last seq applied per scope, for the cursor and the next pull. */
    fun cursor(scope: Scope): Long = applied[scope] ?: 0

    /** A change to the board (v1). */
    fun propose(scope: Scope, op: BoardOp): Proposed =
        propose(scope, BoardJson.json.encodeToJsonElement(BoardOp.serializer(), op) as JsonObject)

    /** A change in any capability, as the op's JSON (a board op, or a `v: 2` op of a namespace). */
    fun propose(scope: Scope, op: JsonObject): Proposed {
        val namespace = FamilyReducer.namespaceOf(op)
        if (stoppedAt != null || namespace in stopped) return Proposed.UpdateRequired
        if (view.partition(scope) == null) return Proposed.ScopeNotHeld
        val opId = (op["opId"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return Proposed.Rejected(RejectCode.INVALID)
        return when (val step = FamilyReducer.apply(view, LogEntry(0, scope, memberId, deviceId, op), namespaces)) {
            is FamilyStep.Applied -> {
                view = step.state
                outbox += PendingOp(scope, opId, op)
                Proposed.Queued(opId)
            }
            is FamilyStep.Rejected -> Proposed.Rejected(step.code)
            is FamilyStep.KeptAside, is FamilyStep.Stopped -> Proposed.UpdateRequired
        }
    }

    /**
     * The server refused to store an op (`E_CONFLICT`, ...): it will never be in the log, so it
     * leaves the outbox and the view.
     */
    fun refused(opId: String): Boolean {
        val removed = outbox.removeAll { it.opId == opId }
        if (removed) rebase(mutableListOf())
        return removed
    }

    /** Applies log entries (one scope or several, any order); see the class comment. */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    fun receive(entries: List<LogEntry>): EngineUpdate {
        val outcomes = mutableListOf<Outcome>()
        val confirmedOwn = mutableListOf<String>()
        val dropped = mutableListOf<Pair<String, RejectCode>>()
        for (entry in entries.sortedBy { it.seq }) {
            if (confirmed.partition(entry.scope) == null) continue
            if (entry.seq <= cursor(entry.scope)) continue
            val stop = stoppedAt
            if (stop != null && entry.seq >= stop) {
                hold(entry)
                continue
            }
            val namespace = FamilyReducer.namespaceOf(entry.op)
            if (namespace != null && namespace in stopped) {
                hold(entry)
                applied[entry.scope] = entry.seq
                continue
            }
            val opId = (entry.op["opId"] as? JsonPrimitive)?.content
            val own = outbox.firstOrNull { it.opId == opId && it.scope == entry.scope }
            when (val step = FamilyReducer.apply(confirmed, entry, namespaces)) {
                is FamilyStep.Applied -> {
                    confirmed = step.state
                    outcomes += Outcome.applied(entry.seq)
                    if (own != null) confirmedOwn += own.opId
                }
                is FamilyStep.Rejected -> {
                    outcomes += Outcome.rejected(entry.seq, step.code)
                    if (own != null) dropped += own.opId to step.code
                }
                is FamilyStep.KeptAside -> {
                    hold(entry)
                    applied[entry.scope] = entry.seq
                    continue
                }
                is FamilyStep.Stopped -> {
                    val stoppedNs = step.namespace
                    hold(entry)
                    if (stoppedNs == null) {
                        stoppedAt = entry.seq
                    } else {
                        stoppedNamespaces.putIfAbsent(stoppedNs, entry.seq)
                        applied[entry.scope] = entry.seq
                    }
                    continue
                }
            }
            if (own != null) outbox.remove(own)
            applied[entry.scope] = entry.seq
        }
        held.sortBy { it.seq }
        rebase(dropped)
        return EngineUpdate(outcomes, confirmedOwn, dropped, updateRequired)
    }

    /** The roster changed (members are lane B's, read from the server): same board, new family. */
    fun updateRoster(family: Family) {
        confirmed = confirmed.copy(family = family)
        rebase(mutableListOf())
    }

    /** This device now holds the parents key: start the parents partition from its first op. */
    fun holdParents() {
        if (confirmed.scopes.parents != null) return
        confirmed = confirmed.copy(scopes = confirmed.scopes.copy(parents = Partition(emptyList(), emptyList())))
        rebase(mutableListOf())
    }

    /**
     * A fresh engine for the same member, device, roster and scopes, with an empty board and this
     * engine's outbox: the app pulls every scope again from seq 0 into it. Used after an update made
     * kept or stopped entries readable ([needsRebuild]).
     */
    fun rebuilt(namespaces: Namespaces = this.namespaces): FamilyEngine = FamilyEngine(
        EngineSnapshot(
            engineVersion = EngineStore.CURRENT,
            memberId = memberId,
            deviceId = deviceId,
            confirmed = empty(confirmed.family, scopes),
            applied = emptyMap(),
            outbox = outbox.toList(),
        ),
        namespaces,
    )

    /**
     * This member's alerts due at [nowMs] that were not [shown] yet, planned from the view (D5).
     * Looks back an hour, so a device that slept still shows the latest of each reminder once.
     */
    fun alerts(
        nowMs: Long,
        shown: Set<String>,
        settings: ReminderSettings = ReminderSettings(),
        snoozes: List<LocalSnooze> = emptyList(),
    ): List<PlannedAlert> {
        val plan = plan(nowMs - LOOK_BACK_MS, nowMs + 1, settings, snoozes)
        return DueAlerts.due(plan, nowMs, shown)
    }

    /** This member's alerts that fire in `[fromMs, toMs)`, for scheduling local alarms by key. */
    fun plan(
        fromMs: Long,
        toMs: Long,
        settings: ReminderSettings = ReminderSettings(),
        snoozes: List<LocalSnooze> = emptyList(),
    ): List<PlannedAlert> = ReminderPlanner.plan(view, fromMs, toMs, settings, snoozes).filter {
        it.recipientId == memberId
    }

    fun snapshot(): EngineSnapshot = EngineSnapshot(
        engineVersion = EngineStore.CURRENT,
        memberId = memberId,
        deviceId = deviceId,
        confirmed = confirmed,
        applied = applied.toMap(),
        outbox = outbox.toList(),
        stoppedAt = stoppedAt,
        stoppedNamespaces = stoppedNamespaces.toMap(),
        held = held.toList(),
    )

    private fun hold(entry: LogEntry) {
        if (held.none { it.seq == entry.seq }) held += entry
    }

    /** Rebuilds the view: the confirmed board, then every pending op that still applies. */
    private fun rebase(dropped: MutableList<Pair<String, RejectCode>>) {
        var next = confirmed
        val keep = mutableListOf<PendingOp>()
        for (pending in outbox) {
            val entry = LogEntry(0, pending.scope, memberId, deviceId, pending.op)
            when (val step = FamilyReducer.apply(next, entry, namespaces)) {
                is FamilyStep.Applied -> {
                    next = step.state
                    keep += pending
                }
                is FamilyStep.Rejected -> dropped += pending.opId to step.code
                is FamilyStep.KeptAside, is FamilyStep.Stopped -> keep += pending
            }
        }
        outbox.clear()
        outbox += keep
        view = next
    }

    companion object {
        private const val LOOK_BACK_MS = ReminderTiming.GRACE_AFTER_START_MINUTES * ReminderTiming.MINUTE

        private fun empty(family: Family, scopes: Set<Scope>) = BoardState(
            schemaVersion = BoardState.SCHEMA_VERSION,
            family = family,
            scopes = Scopes(
                family = Partition(emptyList(), emptyList()),
                parents = Partition(emptyList(), emptyList()).takeIf { Scope.PARENTS in scopes },
            ),
        )

        /** A new device's engine: an empty board for [family], `family` only until it gets the parents key. */
        fun start(
            family: Family,
            memberId: String,
            deviceId: String,
            namespaces: Namespaces = Namespaces.DEFAULT,
        ): FamilyEngine = FamilyEngine(
            EngineSnapshot(
                engineVersion = EngineStore.CURRENT,
                memberId = memberId,
                deviceId = deviceId,
                confirmed = empty(family, setOf(Scope.FAMILY)),
                applied = emptyMap(),
                outbox = emptyList(),
            ),
            namespaces,
        )
    }
}

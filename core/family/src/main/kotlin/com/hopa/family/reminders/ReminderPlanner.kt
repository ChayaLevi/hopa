package com.hopa.family.reminders

import com.hopa.family.model.ActivityKind
import com.hopa.family.model.BoardState
import com.hopa.family.model.FamilyRole
import com.hopa.family.model.Importance
import com.hopa.family.model.Item
import com.hopa.family.model.ItemStatus
import com.hopa.family.model.Ownership
import com.hopa.family.model.Partition
import com.hopa.family.model.Scope
import com.hopa.family.model.UndoEntry
import com.hopa.family.reminders.ReminderTiming.MINUTE
import com.hopa.family.scope.ScopeAccess
import com.hopa.family.time.FamilyClock
import com.hopa.family.time.Occurrences
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Reminders and escalation, computed from the board on every device (D5). A port of Homemind's
 * `reminders.ts`, whose server kept reminder records and ticked them; here the same alerts come out
 * of a pure function of the board, so every device agrees without a server scheduler:
 *
 *  - heads-up: assigned and confirmed, one note before it starts, to the owner;
 *  - confirm: assigned, not confirmed, to the owner;
 *  - open: "Who is taking this?", to the candidates (an unowned item only if it is critical).
 *
 * A regular reminder is one attempt, 30 minutes before. A critical one is three attempts from an hour
 * before, then the other parent (or both, for an open item), then a backup notice if the family set
 * a contact. A regular open item asks both parents 15 minutes before it starts. Nothing fires later
 * than an hour after the start.
 *
 * The times count from when the ask began: the later of "lead time before the start" and the time
 * of the op that made the current ask (read from the activity and its undo patches, which hold the
 * item's earlier versions). An answer is a board change (confirm, take, done), so it reaches every
 * device and the alerts stop everywhere; the owner who just confirmed gets no heads-up as well.
 *
 * Pure: no clock (the window is passed in), no I/O. `spec/fixtures/family/time-reminders.json` (from
 * Homemind's own scheduler, ticked every minute) is the contract.
 */
object ReminderPlanner {
    /**
     * Every alert the board calls for that fires in `[fromMs, toMs)`, for every member. A device
     * keeps those of its own member. [snoozes] are this device's "remind me later" presses.
     */
    fun plan(
        state: BoardState,
        fromMs: Long,
        toMs: Long,
        settings: ReminderSettings = ReminderSettings(),
        snoozes: List<LocalSnooze> = emptyList(),
    ): List<PlannedAlert> {
        val out = mutableListOf<PlannedAlert>()
        val earliestStart = fromMs - ReminderTiming.GRACE_AFTER_START_MINUTES * MINUTE
        val latestStart = toMs + ReminderTiming.LEAD_CRITICAL_MINUTES * MINUTE
        for (scope in listOf(Scope.FAMILY, Scope.PARENTS)) {
            val partition = state.partition(scope) ?: continue
            val asks = Asks(state, scope)
            for (item in partition.items) {
                val ask = asks.askFor(item) ?: continue
                for (occurrence in Occurrences.of(item, earliestStart, latestStart, state.family.timezone)) {
                    val history = AskHistory(asks, partition, item, occurrence.start, state.family.timezone)
                    out += Reminder(state, scope, item, occurrence.date, occurrence.start, ask, history, settings)
                        .alerts(snoozes)
                        .filter { FamilyClock.millis(it.at) in fromMs until toMs }
                }
            }
        }
        return out.sortedWith(compareBy({ it.at }, { it.recipientId }, { it.key }))
    }

    /** One reminder: an item's occurrence and what it asks of whom. */
    private class Reminder(
        val state: BoardState,
        val scope: Scope,
        val item: Item,
        val date: String,
        val start: String,
        val ask: Ask,
        val history: AskHistory,
        val settings: ReminderSettings,
    ) {
        private val startMs = FamilyClock.millis(start)
        private val critical = item.importance == Importance.CRITICAL
        private val tier = if (critical) Tier.URGENT else Tier.TIMED

        @Suppress("CyclomaticComplexMethod", "ReturnCount")
        fun alerts(snoozes: List<LocalSnooze>): List<PlannedAlert> {
            if (ask.ask == ReminderAsk.HEADS_UP && history.answeredBeforeHeadsUp()) return emptyList()
            val since = history.since()
            if (since != null && since >= startMs) return emptyList()
            val lead = if (critical) ReminderTiming.LEAD_CRITICAL_MINUTES else ReminderTiming.LEAD_NORMAL_MINUTES
            val first = max(startMs - lead * MINUTE, since ?: Long.MIN_VALUE)
            val room = (startMs - first).toDouble() / MINUTE

            val attempts = mutableListOf<Long>()
            val escalation = mutableListOf<Pair<Long, Escalate>>()
            when {
                ask.ask == ReminderAsk.HEADS_UP -> attempts += first
                critical -> {
                    val interval = min(
                        ReminderTiming.MAX_INTERVAL_MINUTES,
                        max(ReminderTiming.MIN_INTERVAL_MINUTES, floor(room / CRITICAL_SLOTS).toLong()),
                    )
                    repeat(ReminderTiming.CRITICAL_ATTEMPTS) { attempts += first + it * interval * MINUTE }
                    val to = if (ask.ask == ReminderAsk.CONFIRM) Escalate.NEXT_PARENT else Escalate.BOTH_PARENTS
                    escalation += first + ReminderTiming.CRITICAL_ATTEMPTS * interval * MINUTE to to
                    if (settings.backupContact) {
                        escalation += first + CRITICAL_SLOTS * interval * MINUTE to Escalate.BACKUP
                    }
                }
                ask.ask == ReminderAsk.OPEN -> {
                    attempts += first
                    val after = max(0L, floor(room - ReminderTiming.OPEN_ESCALATE_BEFORE_MINUTES).toLong())
                    escalation += first + after * MINUTE to Escalate.BOTH_PARENTS
                }
                else -> attempts += first
            }

            val lastMs = startMs + ReminderTiming.GRACE_AFTER_START_MINUTES * MINUTE
            val out = mutableListOf<PlannedAlert>()
            for (recipient in ask.recipients) {
                out += attemptsFor(recipient, attempts, snoozes, lastMs)
            }
            escalation.forEachIndexed { index, (time, to) ->
                if (time > lastMs) return@forEachIndexed
                val step = index + 1
                when (to) {
                    Escalate.BACKUP -> parents().forEach { out += alert(it, AlertKind.BACKUP, "b$step", time) }
                    Escalate.BOTH_PARENTS ->
                        parents().forEach { out += alert(it, AlertKind.ESCALATION, "e$step", time) }
                    Escalate.NEXT_PARENT -> parents().filter { it !in ask.recipients }
                        .forEach { out += alert(it, AlertKind.ESCALATION, "e$step", time) }
                }
            }
            return dedupe(out)
        }

        /** The attempts to one recipient, with their snooze: held until it ends, then one more. */
        private fun attemptsFor(
            recipient: String,
            attempts: List<Long>,
            snoozes: List<LocalSnooze>,
            lastMs: Long,
        ): List<PlannedAlert> {
            val key = "${item.id}:$date.${ask.ask.wire}"
            val snooze = snoozes.lastOrNull { it.memberId == recipient && it.reminderKey == key }
            val pressed = snooze?.let { FamilyClock.millis(it.at) }
            val until = pressed?.let { it + ReminderTiming.SNOOZE_MINUTES * MINUTE }
            val out = mutableListOf<PlannedAlert>()
            attempts.forEachIndexed { index, time ->
                val held = pressed != null && until != null && time >= pressed && time <= until
                if (!held && time <= lastMs) out += alert(recipient, AlertKind.REMINDER, "a${index + 1}", time)
            }
            if (pressed != null && until != null && until <= lastMs && attempts.first() <= pressed) {
                out += alert(recipient, AlertKind.REMINDER, "s", until)
            }
            return out
        }

        /** One alert per member, reminder and minute: an escalation replaces that minute's attempt. */
        private fun dedupe(alerts: List<PlannedAlert>): List<PlannedAlert> = alerts
            .groupBy { Triple(it.recipientId, it.at, it.kind == AlertKind.BACKUP) }
            .values
            .map { same -> same.maxBy { it.kind.ordinal } }

        private fun parents(): List<String> = state.family.members
            .filter { it.role == FamilyRole.PARENT && it.hasAccount && ScopeAccess.mayWrite(it, scope) }
            .map { it.id }

        private fun alert(recipient: String, kind: AlertKind, step: String, time: Long) = PlannedAlert(
            itemId = item.id,
            scope = scope,
            date = date,
            occurrenceStart = start,
            ask = ask.ask,
            kind = kind,
            step = step,
            at = FamilyClock.iso(time),
            recipientId = recipient,
            tier = tier,
            itemVersion = item.version,
            sensitive = item.sensitive,
            recurring = item.recurrence != null,
        )
    }

    private enum class Escalate { NEXT_PARENT, BOTH_PARENTS, BACKUP }

    /** Critical: the escalation comes after three intervals, the backup after four. */
    private const val CRITICAL_SLOTS = 4
}

/** What a reminder asks, and of whom (members with an account who hold the item's scope). */
internal data class Ask(
    val ask: ReminderAsk,
    val recipients: List<String>,
)

/** Homemind's `askFor`, with visibility read as the item's scope (D11). */
internal class Asks(private val state: BoardState, private val scope: Scope) {
    private fun visible(ids: List<String>): List<String> = ids.distinct().filter { id ->
        state.family.members.firstOrNull { it.id == id }?.let { ScopeAccess.mayWrite(it, scope) } == true
    }

    private fun parentIds(): List<String> =
        state.family.members.filter { it.role == FamilyRole.PARENT && it.hasAccount }.map { it.id }

    fun askFor(item: Item): Ask? {
        if (item.status != ItemStatus.OPEN || item.start == null || item.allDay == true) return null
        val ask = when (val owned = item.ownership) {
            is Ownership.Assigned ->
                Ask(if (owned.confirmed) ReminderAsk.HEADS_UP else ReminderAsk.CONFIRM, visible(listOf(owned.ownerId)))
            is Ownership.Open -> {
                val candidates = visible(owned.candidateIds)
                Ask(ReminderAsk.OPEN, candidates.ifEmpty { visible(parentIds()) })
            }
            Ownership.None -> {
                // Nobody owns it and nobody was asked: only a critical item (a child's pickup) is worth it.
                if (item.importance != Importance.CRITICAL) return null
                Ask(ReminderAsk.OPEN, visible(ScopeAccess.eligibleOwners(state.family, scope)))
            }
        }
        return ask.takeIf { it.recipients.isNotEmpty() }
    }
}

/**
 * When the current ask for one occurrence began, read from the item's history in its partition:
 * each activity's undo patch holds the item as it was before that op. Walking back from now, the
 * first op before which the ask (or its recipients, or the occurrence's start) was different is the
 * one that began it. No such op (a seeded item): it has always been so.
 */
internal class AskHistory(
    private val asks: Asks,
    partition: Partition,
    private val item: Item,
    private val start: String,
    private val zone: String,
) {
    private data class Step(val at: Long, val before: Item?, val known: Boolean)

    private fun touches(entries: List<UndoEntry>): Boolean = entries.any { it.itemId == item.id }

    private val steps: List<Step> = partition.activity
        .filter { activity -> activity.itemId == item.id || touches(activity.undo?.items.orEmpty()) }
        .asReversed()
        .map { activity ->
            val entry = activity.undo?.items?.firstOrNull { it.itemId == item.id }
            // An undo has no patch of its own: what came before it is not known here.
            Step(
                FamilyClock.millis(activity.at),
                entry?.before,
                known = activity.kind != ActivityKind.UNDO && entry != null,
            )
        }

    /** The ask [version] made for this occurrence, or null when it had no occurrence at this start. */
    private fun askAt(version: Item?): Ask? {
        if (version == null) return null
        val startMs = FamilyClock.millis(start)
        if (Occurrences.of(version, startMs, startMs + 1, zone).none { it.start == start }) return null
        return asks.askFor(version)
    }

    private fun same(a: Ask?, b: Ask?): Boolean =
        a != null && b != null && a.ask == b.ask && a.recipients.sorted() == b.recipients.sorted()

    /** The index of the step that began the ask now in force at [from], or -1 when it never changed. */
    private fun changeIndex(from: Int, current: Ask?): Int {
        for (i in from until steps.size) {
            val step = steps[i]
            if (!step.known || !same(askAt(step.before), current)) return i
        }
        return -1
    }

    /** When the current ask began, or null when it has always been so. */
    fun since(): Long? = changeIndex(0, asks.askFor(item)).takeIf { it >= 0 }?.let { steps[it].at }

    /**
     * Homemind does not send a heads-up after the owner answered an ask that had already gone out:
     * they just said they are on it. True when the heads-up began by an op that answered a confirm or
     * open ask for this occurrence whose first attempt was before that op.
     */
    fun answeredBeforeHeadsUp(): Boolean {
        val index = changeIndex(0, asks.askFor(item))
        if (index < 0) return false
        val step = steps[index]
        if (!step.known) return false
        val before = step.before ?: return false
        val previous = askAt(before) ?: return false
        if (previous.ask == ReminderAsk.HEADS_UP) return false
        val earlier = changeIndex(index + 1, previous)
        val previousSince = if (earlier >= 0) steps[earlier].at else null
        val startMs = FamilyClock.millis(start)
        val lead = if (before.importance == Importance.CRITICAL) {
            ReminderTiming.LEAD_CRITICAL_MINUTES
        } else {
            ReminderTiming.LEAD_NORMAL_MINUTES
        }
        if (previousSince != null && previousSince >= startMs) return false
        val previousFirst = max(startMs - lead * MINUTE, previousSince ?: Long.MIN_VALUE)
        return previousFirst < step.at
    }
}

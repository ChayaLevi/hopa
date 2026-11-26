package com.hopa.family.reminders

import com.hopa.family.model.BoardState
import com.hopa.family.model.FamilyRole
import com.hopa.family.model.Importance
import com.hopa.family.model.Item
import com.hopa.family.model.Scope
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

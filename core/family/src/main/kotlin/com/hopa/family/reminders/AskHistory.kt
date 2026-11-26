package com.hopa.family.reminders

import com.hopa.family.model.ActivityKind
import com.hopa.family.model.Importance
import com.hopa.family.model.Item
import com.hopa.family.model.Partition
import com.hopa.family.model.UndoEntry
import com.hopa.family.reminders.ReminderTiming.MINUTE
import com.hopa.family.time.FamilyClock
import com.hopa.family.time.Occurrences
import kotlin.math.max

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

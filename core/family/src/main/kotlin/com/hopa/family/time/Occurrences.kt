package com.hopa.family.time

import com.hopa.family.model.Item
import com.hopa.family.model.ItemStatus

/** One start of an item: its local date in the family's zone, and its start and end instants. */
data class Occurrence(
    val itemId: String,
    val date: String,
    val start: String,
    val end: String? = null,
)

/**
 * The occurrences of an item, as Homemind's `occurrences` (`core.ts`). A one-off item gives itself
 * when it starts in range. A weekly item repeats at the same local time in the family's zone, so it
 * stays at 16:30 across a daylight-saving change, and skips its cancelled dates.
 */
object Occurrences {
    /** Safety bound on how many days one expansion walks. */
    private const val MAX_EXPAND_DAYS = 400

    /** The occurrences of [item] that start in `[fromMs, toMs)`. */
    fun of(item: Item, fromMs: Long, toMs: Long, zone: String): List<Occurrence> {
        val start = item.start ?: return emptyList()
        if (item.status == ItemStatus.CANCELLED) return emptyList()
        val startMs = FamilyClock.millis(start)
        val duration = item.end?.let { FamilyClock.millis(it) - startMs }
        fun make(date: String, ms: Long) = Occurrence(
            itemId = item.id,
            date = date,
            start = FamilyClock.iso(ms),
            end = duration?.let { FamilyClock.iso(ms + it) },
        )
        val recurrence = item.recurrence
            ?: return if (startMs in fromMs until toMs) {
                listOf(make(FamilyClock.localParts(startMs, zone).date, startMs))
            } else {
                emptyList()
            }

        val first = FamilyClock.localParts(startMs, zone)
        val exceptions = recurrence.exceptions.orEmpty().toSet()
        val fromDate = FamilyClock.localParts(fromMs, zone).date
        val lastDate = FamilyClock.localParts(toMs, zone).date
        var date = maxOf(first.date, fromDate)
        val out = mutableListOf<Occurrence>()
        var walked = 0
        while (walked < MAX_EXPAND_DAYS && date <= lastDate) {
            val repeats = recurrence.until.let { it == null || date <= it } &&
                FamilyClock.weekday(date) in recurrence.byWeekday
            if (repeats && date !in exceptions) {
                val ms = FamilyClock.zonedToUtc(date, first.time, zone)
                if (ms in fromMs until toMs) out += make(date, ms)
            }
            date = FamilyClock.addDays(date, 1)
            walked += 1
        }
        return out
    }
}

package com.hopa.family.intents

import java.time.LocalDate
import java.time.ZoneOffset

/**
 * `Date.parse` for the ISO datetimes Homemind's validators accept
 * (`YYYY-MM-DDTHH:MM[:SS[.fff]](Z|±HH:MM)`), with JavaScript's own leniency: a day up to 31 rolls
 * over past the end of the month (2026-11-31 is 1 December), 24:00 is the next midnight, and an
 * offset's hours go up to 23. Anything else JavaScript refuses is null. Pinned by the `dates` cases
 * of `spec/fixtures/family/logic-intents.json`.
 */
internal object JsDate {
    private val ISO =
        Regex("""^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3}))?)?(Z|([+-])(\d{2}):(\d{2}))$""")
    private const val MAX_DAY = 31
    private const val MAX_MONTH = 12
    private const val END_OF_DAY = 24
    private const val MAX_MINUTE = 59
    private const val MAX_OFFSET_HOURS = 23
    private const val MILLIS = 1000L
    private const val MINUTE_MS = 60_000L
    private const val HOUR_MS = 3_600_000L

    @Suppress("ReturnCount", "CyclomaticComplexMethod")
    fun parse(text: String): Long? {
        val m = ISO.matchEntire(text) ?: return null
        val g = m.groupValues
        val year = g[1].toInt()
        val month = g[2].toInt()
        val day = g[3].toInt()
        val hour = g[4].toInt()
        val minute = g[5].toInt()
        val second = g[6].ifEmpty { "0" }.toInt()
        val millis = g[7].ifEmpty { "0" }.padEnd(3, '0').toInt()
        if (month !in 1..MAX_MONTH || day !in 1..MAX_DAY) return null
        if (minute > MAX_MINUTE || second > MAX_MINUTE) return null
        if (hour > END_OF_DAY || (hour == END_OF_DAY && (minute != 0 || second != 0 || millis != 0))) return null
        var offsetMs = 0L
        if (g[8] != "Z") {
            val oh = g[10].toInt()
            val om = g[11].toInt()
            if (oh > MAX_OFFSET_HOURS || om > MAX_MINUTE) return null
            offsetMs = (oh * HOUR_MS + om * MINUTE_MS) * if (g[9] == "-") -1 else 1
        }
        val dayStart = LocalDate.of(
            year,
            month,
            1,
        ).plusDays(day - 1L).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        return dayStart + hour * HOUR_MS + minute * MINUTE_MS + second * MILLIS + millis - offsetMs
    }
}

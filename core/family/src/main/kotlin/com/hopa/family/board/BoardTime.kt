package com.hopa.family.board

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Times as Homemind reads them. Nothing here reads a clock: every instant comes from an op or the
 * state (D10).
 */
internal object BoardTime {
    private val ISO_UTC = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,3})?)?Z$""")
    private val CANONICAL = Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$""")
    private const val NO_SECONDS_LENGTH = 17
    private val LOCAL_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val WRITE: DateTimeFormatter = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
    ).withZone(ZoneOffset.UTC)

    /** An ISO time in UTC, written in the one canonical form (`toIso`), or null when it is not one. */
    fun normalize(value: String): String? {
        if (!ISO_UTC.matches(value)) return null
        // Homemind accepts a time without seconds ("...T12:30Z"); Instant.parse needs them.
        val full = if (value.length == NO_SECONDS_LENGTH) value.dropLast(1) + ":00Z" else value
        return runCatching { WRITE.format(Instant.parse(full)) }.getOrNull()
    }

    /** True for an instant already in the canonical form, as an op's `at` must be. */
    fun isCanonical(value: String): Boolean = CANONICAL.matches(value) && normalize(value) == value

    fun millis(value: String): Long = Instant.parse(value).toEpochMilli()

    fun isLocalDate(value: String): Boolean = LOCAL_DATE.matches(value)

    /** The local date (`YYYY-MM-DD`) of an instant in the family's zone. */
    fun localDate(instant: String, timezone: String): String =
        Instant.parse(instant).atZone(ZoneId.of(timezone)).toLocalDate().toString()

    /** 0 = Sunday ... 6 = Saturday, or null for a date that does not exist. */
    fun weekday(date: String): Int? = runCatching { LocalDate.parse(date).dayOfWeek.value % 7 }.getOrNull()
}

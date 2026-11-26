package com.hopa.family.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * The family's clock rules: local dates and times in the family's zone, as Homemind's `clock.ts`
 * computes them. Nothing here reads a clock; every instant is passed in, in milliseconds since the
 * epoch. `spec/fixtures/family/time-zones.json` is the contract.
 */
object FamilyClock {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val ISO: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC)

    /** The local date (`YYYY-MM-DD`), time (`HH:MM`) and weekday (0 = Sunday) of [millis] in [zone]. */
    fun localParts(millis: Long, zone: String): LocalParts {
        val local = Instant.ofEpochMilli(millis).atZone(ZoneId.of(zone))
        return LocalParts(
            date = local.toLocalDate().toString(),
            time = local.toLocalTime().truncatedTo(ChronoUnit.MINUTES).format(TIME),
            weekday = local.dayOfWeek.value % DAYS_IN_WEEK,
        )
    }

    /**
     * The instant of a local [date] and [time] (`HH:MM`) in [zone], by Homemind's `zonedToUtc`
     * algorithm, step for step, so every client lands on the same instant: guess the offset twice,
     * keep the candidates that read back as the asked local time, and take the earlier of those; if
     * none does (a daylight-saving gap), the later candidate, which moves the time forward by the gap.
     * In a repeated hour this does not always find the earlier instant (Jerusalem, 25 October 2026,
     * 01:30 gives the later one); the fixtures pin that.
     */
    fun zonedToUtc(date: String, time: String, zone: String): Long {
        val id = ZoneId.of(zone)
        val guess = LocalDateTime.of(
            LocalDate.parse(date),
            LocalTime.parse(time),
        ).toInstant(ZoneOffset.UTC).toEpochMilli()
        val first = guess - offsetMillis(guess, id)
        val second = guess - offsetMillis(first, id)
        val exact = listOf(first, second).filter { localParts(it, zone).let { p -> p.date == date && p.time == time } }
        return exact.minOrNull() ?: maxOf(first, second)
    }

    private fun offsetMillis(millis: Long, zone: ZoneId): Long =
        zone.rules.getOffset(Instant.ofEpochMilli(millis)).totalSeconds * MILLIS_PER_SECOND

    fun addDays(date: String, days: Long): String = LocalDate.parse(date).plusDays(days).toString()

    /** 0 = Sunday ... 6 = Saturday. */
    fun weekday(date: String): Int = LocalDate.parse(date).dayOfWeek.value % DAYS_IN_WEEK

    fun millis(iso: String): Long = Instant.parse(iso).toEpochMilli()

    /** An instant in the one canonical ISO form (`2026-11-11T12:30:00.000Z`). */
    fun iso(millis: Long): String = ISO.format(Instant.ofEpochMilli(millis))

    private const val DAYS_IN_WEEK = 7
    private const val MILLIS_PER_SECOND = 1000L
}

data class LocalParts(
    val date: String,
    val time: String,
    val weekday: Int,
)

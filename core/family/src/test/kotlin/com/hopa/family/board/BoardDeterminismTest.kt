package com.hopa.family.board

import com.hopa.family.model.LogEntry
import com.hopa.family.model.Scope
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.util.Locale
import java.util.TimeZone

/**
 * Phase S.4: replay is deterministic. Each test runs simulated devices ([DeviceEngine]) over the
 * fixture logs and over [FixtureLogs.combined], one long log in which the cases and both scopes
 * interleave.
 *  (a) two engines that get the same log in different deliveries, or on a device with another
 *      locale and time zone, reach the same state hash;
 *  (b) a parent engine (both scopes) and a teen engine (`family` only) hold the same `family`
 *      partition after every entry;
 *  (c) an unknown op kind or a higher `v` stops replay at that seq (D15).
 */
class BoardDeterminismTest {
    private val seed = FixtureLogs.seed
    private val both = setOf(Scope.FAMILY, Scope.PARENTS)
    private val familyOnly = setOf(Scope.FAMILY)

    private fun logs(): List<Pair<String, List<LogEntry>>> =
        FixtureLogs.logs.mapIndexed { i, log -> "case $i" to log } + ("combined" to FixtureLogs.combined)

    private fun engine(held: Set<Scope>, log: List<LogEntry>, batch: Int): DeviceEngine =
        DeviceEngine(seed, held).also { e -> log.chunked(batch).forEach(e::receive) }

    // (a) two engines ----------------------------------------------------------------------------

    @Test
    fun `the combined log mixes both scopes, applied and rejected`() {
        val log = FixtureLogs.combined
        val outcomes = engine(both, log, log.size).outcomes
        val scopeOf = log.associate { it.seq to it.scope }
        for (scope in Scope.entries) {
            val mine = outcomes.filter { scopeOf[it.seq] == scope }
            assertTrue(mine.count { it.outcome == "applied" } >= MIN_APPLIED[scope]!!, "$scope applied: $mine")
            assertTrue(mine.any { it.outcome == "rejected" }, "$scope has no rejected entry")
        }
        assertEquals(log.size, outcomes.size)
    }

    @TestFactory
    fun `two engines reach the same hash whatever the deliveries`(): List<DynamicTest> = logs().map { (name, log) ->
        DynamicTest.dynamicTest(name) {
            val a = engine(both, log, log.size)
            for (batch in BATCHES) {
                val b = engine(both, log, batch)
                assertEquals(a.hash(), b.hash(), "state hash, batches of $batch")
                assertEquals(a.outcomes, b.outcomes, "outcomes, batches of $batch")
                assertNull(b.stoppedAt)
            }
        }
    }

    @Test
    fun `an engine with another locale and time zone reaches the same hash`() {
        val log = FixtureLogs.renumber(FixtureLogs.combined + nearMidnight())
        val reference = engine(both, log, 1)
        val cancel = log[log.size - 2].seq
        assertEquals(
            Outcome.applied(cancel),
            reference.outcomes.single { it.seq == cancel },
            "Monday in the family's zone",
        )
        val locale = Locale.getDefault()
        val zone = TimeZone.getDefault()
        try {
            for ((tag, tz) in DEVICE_SETTINGS) {
                Locale.setDefault(Locale.forLanguageTag(tag))
                TimeZone.setDefault(TimeZone.getTimeZone(tz))
                val other = engine(both, log, 1)
                assertEquals(reference.hash(), other.hash(), "state hash in $tag, $tz")
                assertEquals(reference.outcomes, other.outcomes, "outcomes in $tag, $tz")
            }
        } finally {
            Locale.setDefault(locale)
            TimeZone.setDefault(zone)
        }
    }

    @Test
    fun `a replay never changes its input and gives the same result twice`() {
        val log = FixtureLogs.combined
        val seedHash = BoardHash.of(seed)
        val first = BoardReplay.replay(seed, log, both)
        val second = BoardReplay.replay(seed, log, both)
        assertEquals(seedHash, BoardHash.of(seed), "seed changed")
        assertEquals(BoardHash.of(first.state), BoardHash.of(second.state))
        assertEquals(first.outcomes, second.outcomes)
        assertNotEquals(seedHash, BoardHash.of(first.state), "the log changed nothing")
    }

    // (b) parent and teen ------------------------------------------------------------------------

    @TestFactory
    fun `a parent and a teen hold the same family partition after every entry`(): List<DynamicTest> =
        logs().map { (name, log) ->
            DynamicTest.dynamicTest(name) {
                val parent = DeviceEngine(seed, both)
                val teen = DeviceEngine(seed, familyOnly)
                for (entry in log) {
                    val familyBefore = parent.hash(Scope.FAMILY)
                    parent.receive(listOf(entry))
                    teen.receive(listOf(entry))
                    assertEquals(
                        parent.hash(Scope.FAMILY),
                        teen.hash(Scope.FAMILY),
                        "family hash after seq ${entry.seq}",
                    )
                    if (entry.scope == Scope.PARENTS) {
                        assertEquals(familyBefore, parent.hash(Scope.FAMILY), "parents seq ${entry.seq} changed family")
                    }
                }
                assertNull(teen.state.scopes.parents, "a teen holds no parents partition")
                val familySeqs = log.filter { it.scope == Scope.FAMILY }.map { it.seq }.toSet()
                assertEquals(parent.outcomes.filter { it.seq in familySeqs }, teen.outcomes, "teen outcomes")
            }
        }

    // (c) unknown op stops -----------------------------------------------------------------------

    @TestFactory
    fun `an unknown kind or a higher v stops replay at that seq`(): List<DynamicTest> {
        val log = FixtureLogs.combined
        return STOP_POSITIONS.flatMap { at ->
            listOf("unknown kind" to unknownKind(log[at]), "v 2" to higherVersion(log, at)).map { (what, op) ->
                DynamicTest.dynamicTest("$what at position $at") { checkStop(log, at, op, Scope.FAMILY) }
            }
        }
    }

    /**
     * D15 as written: a parent stuck on an unknown `parents` op stops applying `family` ops too,
     * while a teen, who is never sent that entry, goes on. Both hold the same family partition up
     * to the stop, and the parent catches up once it can read the op.
     */
    @Test
    fun `an unknown parents op stops the parent but not the teen`() {
        val log = FixtureLogs.combined
        val at = log.indexOfFirst { it.scope == Scope.PARENTS && it.seq > log.size / 2 }
        val withStop = insert(log, at, Scope.PARENTS, unknownKind(log[at]))
        val stopSeq = at + 1L

        val parent = engine(both, withStop, 1)
        val teen = engine(familyOnly, withStop, 1)
        assertEquals(stopSeq, parent.stoppedAt)
        assertNull(teen.stoppedAt)

        val beforeStop = withStop.filter { it.seq < stopSeq }
        assertEquals(engine(familyOnly, beforeStop, 1).hash(Scope.FAMILY), parent.hash(Scope.FAMILY))
        assertEquals(engine(familyOnly, FixtureLogs.renumber(log), 1).hash(Scope.FAMILY), teen.hash(Scope.FAMILY))
        assertTrue(withStop.any { it.seq > stopSeq && it.scope == Scope.FAMILY }, "no family entry after the stop")
    }

    private fun checkStop(log: List<LogEntry>, at: Int, op: JsonObject, scope: Scope) {
        val withStop = insert(log, at, scope, op)
        val stopSeq = at + 1L
        val prefix = withStop.filter { it.seq < stopSeq }
        val expected = engine(both, prefix, prefix.size.coerceAtLeast(1))

        for (batch in listOf(withStop.size, 1, 5)) {
            val device = engine(both, withStop, batch)
            assertEquals(stopSeq, device.stoppedAt, "stoppedAt, batches of $batch")
            assertEquals(expected.hash(), device.hash(), "state at the stop, batches of $batch")
            assertEquals(expected.outcomes, device.outcomes, "outcomes, batches of $batch")
            assertTrue(device.outcomes.all { it.seq < stopSeq }, "an entry after the stop has an outcome")
            assertEquals(withStop.filter { it.seq >= stopSeq }.map { it.seq }, device.queue.map { it.seq }, "queue")

            // More deliveries change nothing: the device waits for an update.
            device.receive(listOf(withStop.last().copy(seq = withStop.size + 1L)))
            assertEquals(expected.hash(), device.hash(), "state after a later delivery")
        }
    }

    /** [log] with an entry of [scope] carrying [op] inserted at index [at], renumbered from seq 1. */
    private fun insert(log: List<LogEntry>, at: Int, scope: Scope, op: JsonObject): List<LogEntry> {
        val author = log.first { it.scope == scope }
        val entry =
            LogEntry(seq = 0, scope = scope, memberId = author.memberId, fromDevice = author.fromDevice, op = op)
        return FixtureLogs.renumber(log.take(at) + entry + log.drop(at))
    }

    /**
     * A weekly Monday event that starts at 22:00 in the family's zone (Asia/Jerusalem), which is
     * already Tuesday in Kiritimati, then a cancel of that first Monday (applied: the start date is
     * read in the family's zone, never the device's) and of the Monday before it (invalid).
     */
    private fun nearMidnight(): List<LogEntry> {
        val create = """
            {"v":1,"kind":"create","opId":"op-late-create","at":"2026-11-11T12:40:00.000Z",
             "item":{"id":"late-weekly","type":"event","title":"Late call",
                     "start":"2026-11-16T20:00:00.000Z","end":"2026-11-16T21:00:00.000Z",
                     "recurrence":{"freq":"weekly","byWeekday":[1]}}}
        """
        val cancelFirst = """
            {"v":1,"kind":"cancel_occurrence","opId":"op-late-cancel","at":"2026-11-11T12:41:00.000Z",
             "itemId":"late-weekly","version":1,"date":"2026-11-16"}
        """
        val cancelBefore = """
            {"v":1,"kind":"cancel_occurrence","opId":"op-late-before","at":"2026-11-11T12:42:00.000Z",
             "itemId":"late-weekly","version":2,"date":"2026-11-09"}
        """
        return listOf(create, cancelFirst, cancelBefore).map { op ->
            LogEntry(
                seq = 0,
                scope = Scope.FAMILY,
                memberId = "mom",
                fromDevice = "mom-phone",
                op = BoardJson.json.parseToJsonElement(op).jsonObject,
            )
        }
    }

    private fun unknownKind(next: LogEntry): JsonObject = JsonObject(
        mapOf(
            "v" to JsonPrimitive(1),
            "kind" to JsonPrimitive("archive"),
            "opId" to JsonPrimitive("op-unknown-${next.seq}"),
            "at" to next.op.getValue("at"),
            "itemId" to JsonPrimitive("t-unknown"),
        ),
    )

    /** A valid op from the log, written as a later version: this reader must not guess at it. */
    private fun higherVersion(log: List<LogEntry>, at: Int): JsonObject {
        val model = log.drop(at).first { it.scope == Scope.FAMILY }.op
        return JsonObject(model + mapOf("v" to JsonPrimitive(2), "opId" to JsonPrimitive("op-v2-$at")))
    }

    private companion object {
        val BATCHES = listOf(1, 2, 3, 7)
        val STOP_POSITIONS = listOf(0, 1, 17, 40, 77)
        val MIN_APPLIED = mapOf(Scope.FAMILY to 20, Scope.PARENTS to 3)

        /** Arabic and Thai digits, a Turkish dotless i, and zones far from UTC on both sides. */
        val DEVICE_SETTINGS = listOf(
            "ar-EG" to "Pacific/Kiritimati",
            "th-TH-u-nu-thai" to "Pacific/Pago_Pago",
            "tr-TR" to "America/St_Johns",
            "hi-IN-u-nu-deva" to "Asia/Kathmandu",
        )
    }
}

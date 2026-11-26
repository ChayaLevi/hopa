package com.hopa.family.summary

import com.hopa.family.time.FamilyClock
import com.hopa.family.time.TimeFixtures
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** `logic-summary.json`: Homemind's summaryFor against this port, for every member, kind and instant. */
class SummaryFixturesTest {
    private val file = TimeFixtures.file("logic-summary.json")
    private val weekStartsOn = file.getValue("weekStartsOn").jsonPrimitive.int

    @TestFactory
    fun `every summary matches Homemind`(): List<DynamicTest> = file.getValue("cases").jsonArray.map {
        it.jsonObject
    }.map { case ->
        DynamicTest.dynamicTest(case.getValue("name").jsonPrimitive.content) { check(case) }
    }

    private fun JsonObject.text(field: String) = getValue(field).jsonPrimitive.content

    private fun check(case: JsonObject) {
        val state = TimeFixtures.stateAt(TimeFixtures.start(case), TimeFixtures.log(case), Long.MAX_VALUE)
        val unanswered = case.getValue("unanswered").jsonArray.map { it.jsonObject }
        val mismatches = case.getValue("summaries").jsonArray.map { it.jsonObject }.mapNotNull { want ->
            val member = want.text("memberId")
            val kind = SummaryKind.entries.single { it.wire == want.text("kind") }
            val mine = unanswered.filter { u ->
                u.getValue(
                    "memberIds",
                ).jsonArray.any { it.jsonPrimitive.content == member }
            }
                .map { it.text("itemId") }.toSet()
            val got = Summaries.summaryFor(
                state,
                member,
                kind,
                FamilyClock.millis(want.text("now")),
                weekStartsOn,
                mine,
            )!!
            val expected = listOf(
                want.text("date"),
                want.text("from"),
                want.text("to"),
                want.text("scheduledAt"),
                want.getValue("toDo").jsonArray.map { it.jsonObject }
                    .map { listOf(it.text("itemId"), it.text("why"), it["date"]?.jsonPrimitive?.content) },
                want.getValue("away").jsonArray.map { it.jsonObject }
                    .map {
                        listOf(
                            it.text("memberId"),
                            it.text("eventId"),
                            it.text("date"),
                            it.text("start"),
                            it.text("end"),
                        )
                    },
                want.getValue(
                    "everyone",
                ).jsonArray.map { it.jsonObject }.map { listOf(it.text("itemId"), it.text("date")) },
            )
            val actual = listOf(
                got.date,
                got.from,
                got.to,
                got.scheduledAt,
                got.toDo.map { listOf(it.itemId, it.why.wire, it.date) },
                got.away.map { listOf(it.memberId, it.eventId, it.date, it.start, it.end) },
                got.everyone.map { listOf(it.itemId, it.date) },
            )
            if (expected == actual) {
                null
            } else {
                "${want.text(
                    "now",
                )} $member ${kind.wire}:\n  want $expected\n  got  $actual"
            }
        }
        assertEquals(emptyList<String>(), mismatches)
    }
}

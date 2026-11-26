package com.hopa.family.situations

import com.hopa.contracts.json.CanonicalJson
import com.hopa.family.board.BoardJson
import com.hopa.family.model.BoardOp
import com.hopa.family.model.Item
import com.hopa.family.model.LogEntry
import com.hopa.family.model.Scope
import com.hopa.family.scope.OpStamp
import com.hopa.family.time.FamilyClock
import com.hopa.family.time.TimeFixtures
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * `logic-situations.json`: Homemind's planSituation, startSituation and applySuggestions against
 * this port. The plan must match field by field; the stay-home task and every suggestion, written
 * as board ops and applied by the reducer, must leave the same items in each scope. The one
 * difference is pinned: v1 ops carry no Chinese title, so the stay-home task has none.
 */
class SituationFixturesTest {
    private val cases = TimeFixtures.cases("logic-situations.json")

    @TestFactory
    fun `every report matches Homemind`(): List<DynamicTest> = cases.flatMap { case ->
        case.getValue("reports").jsonArray.map { it.jsonObject }.mapIndexed { i, report ->
            DynamicTest.dynamicTest(
                "${case.text("name")} #${i + 1} (${report.text("reporter")})",
            ) { check(case, report) }
        }
    }

    private fun JsonObject.text(field: String) = getValue(field).jsonPrimitive.content

    private fun check(case: JsonObject, want: JsonObject) {
        val state = TimeFixtures.stateAt(TimeFixtures.start(case), TimeFixtures.log(case), Long.MAX_VALUE)
        val reporter = want.text("reporter")
        val report = SituationReport(want.text("memberId"), want.text("date"))
        val now = FamilyClock.millis(want.text("now"))
        val refused = Situations.check(state, reporter, report, now)
        if ("refused" in want) {
            assertEquals(want.text("refused"), refused?.wire)
            return
        }
        assertEquals(null, refused)

        val plan = Situations.plan(state, reporter, report, now)
        val wantPlan = want.getValue("plan").jsonObject
        assertEquals(
            wantPlan.getValue("proposals").jsonArray.map { p ->
                val o = p.jsonObject
                listOf(
                    o.text("id"),
                    o.text("kind"),
                    o.text("itemId"),
                    o["start"]?.jsonPrimitive?.content,
                    o["newStart"]?.jsonPrimitive?.content,
                    o["newEnd"]?.jsonPrimitive?.content,
                    o["date"]?.jsonPrimitive?.content,
                    o["candidateIds"]?.jsonArray?.map { it.jsonPrimitive.content },
                )
            },
            plan.proposals.map {
                listOf(it.id, it.kind.wire, it.itemId, it.start, it.newStart, it.newEnd, it.date, it.candidateIds)
            },
            "proposals",
        )
        assertEquals(strings(wantPlan.getValue("available")), plan.available, "available")
        assertEquals(strings(wantPlan.getValue("parents")), plan.parents, "parents")
        assertEquals(
            wantPlan.getValue("away").jsonArray.map {
                it.jsonObject
            }.map { listOf(it.text("memberId"), it.text("eventId"), it.text("start"), it.text("end")) },
            plan.away.map { listOf(it.memberId, it.eventId, it.start, it.end) },
            "away",
        )

        // The stay-home task, then every suggestion, as the reporter's ops through the reducer.
        var n = 0
        fun stamp() = OpStamp("op-situation-${++n}", want.text("now"))
        val stayHomeWant = want["stayHome"]?.jsonObject
        val ops = buildList {
            val stayHome = Situations.stayHome(
                state,
                reporter,
                report,
                plan,
                stayHomeWant?.text("id") ?: "unused",
                stamp(),
                want.getValue("mayWrite").jsonPrimitive.boolean,
            )
            assertEquals(stayHomeWant != null, stayHome != null, "stay-home task")
            stayHome?.let(::add)
        }
        var after = TimeFixtures.stateAt(
            state,
            ops.mapIndexed { i, op -> entry(i + 1L, reporter, op.scope, op.op) },
            Long.MAX_VALUE,
        )
        for (proposal in plan.proposals) {
            val result = Situations.opFor(after, proposal, stamp()) as Situations.Result.Op
            val next = entry(1, reporter, result.op.scope, result.op.op)
            after = TimeFixtures.stateAt(after, listOf(next), Long.MAX_VALUE)
        }
        val itemsAfter = want.getValue("itemsAfter").jsonObject
        for (scope in Scope.entries) {
            val expected = itemsAfter.getValue(scope.name.lowercase()).jsonArray.map { item ->
                val o = item.jsonObject
                if (o.text("id") == stayHomeWant?.text("id")) JsonObject(o - "titleZh") else o
            }
            val actual = BoardJson.json.encodeToJsonElement(
                ListSerializer(Item.serializer()),
                after.partition(scope)!!.items,
            )
            assertEquals(CanonicalJson.encode(JsonArray(expected)), CanonicalJson.encode(actual), "items in $scope")
        }
    }

    private fun strings(element: JsonElement) = element.jsonArray.map { it.jsonPrimitive.content }

    private fun entry(seq: Long, member: String, scope: Scope, op: BoardOp) = LogEntry(
        seq = seq,
        scope = scope,
        memberId = member,
        fromDevice = "$member-device",
        op = BoardJson.json.encodeToJsonElement(BoardOp.serializer(), op).jsonObject,
    )
}

package com.hopa.family.availability

import com.hopa.contracts.json.CanonicalJson
import com.hopa.family.board.BoardJson
import com.hopa.family.model.BoardOp
import com.hopa.family.model.Item
import com.hopa.family.model.LogEntry
import com.hopa.family.model.Scope
import com.hopa.family.scope.OpStamp
import com.hopa.family.scope.OpStamps
import com.hopa.family.scope.ScopedOp
import com.hopa.family.time.FamilyClock
import com.hopa.family.time.TimeFixtures
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** `time-availability.json`: conflicts, who is unavailable, and the proposals, per scope. */
class AvailabilityFixturesTest {
    private val cases = TimeFixtures.cases("time-availability.json")

    @TestFactory
    fun `every case matches Homemind`(): List<DynamicTest> = cases.map { case ->
        DynamicTest.dynamicTest(case.getValue("name").jsonPrimitive.content) { check(case) }
    }

    private fun JsonObject.text(field: String) = getValue(field).jsonPrimitive.content

    private fun check(case: JsonObject) {
        val log = TimeFixtures.log(case)
        val state = TimeFixtures.stateAt(TimeFixtures.start(case), log, Long.MAX_VALUE)
        val zone = state.family.timezone
        val from = FamilyClock.millis(case.text("from"))
        val to = FamilyClock.millis(case.text("to"))

        val conflicts = Scope.entries.associateWith { scope ->
            Availability.findConflicts(requireNotNull(state.partition(scope)), zone, from, to)
        }
        for (scope in Scope.entries) {
            val want = case.getValue("conflicts").jsonObject.getValue(scope.name.lowercase()).jsonArray.map {
                val c = it.jsonObject
                Conflict(
                    c.text("memberId"),
                    c.text("eventId"),
                    c.text("itemId"),
                    c.text("date"),
                    c.text("start"),
                    c["end"]?.jsonPrimitive?.content,
                    c.getValue("recurring").jsonPrimitive.boolean,
                )
            }
            assertEquals(want, conflicts.getValue(scope), "conflicts in $scope")
        }

        for ((member, rows) in case.getValue("unavailability").jsonObject) {
            val want = rows.jsonArray.map {
                val u = it.jsonObject
                Unavailability(u.text("memberId"), u.text("eventId"), u.text("date"), u.text("start"), u.text("end"))
            }
            assertEquals(want, Availability.unavailabilityFor(state, member, from, to), "unavailability for $member")
        }

        for (range in case.getValue("during").jsonArray.map { it.jsonObject }) {
            val start = FamilyClock.millis(range.text("start"))
            val end = range["end"]?.jsonPrimitive?.content?.let(FamilyClock::millis)
            val parent = range.getValue("parentDevice").jsonArray.map { it.jsonPrimitive.content }
            val family = range.getValue("familyDevice").jsonArray.map { it.jsonPrimitive.content }
            assertEquals(parent, Availability.unavailableDuring(state, start, end), "parent device $range")
            assertEquals(
                family,
                Availability.unavailableDuring(state.viewOf(setOf(Scope.FAMILY)), start, end),
                "family $range",
            )
        }

        val propose = case["propose"]?.jsonObject ?: return
        val proposals = case.getValue("proposals").jsonObject
        for (scope in Scope.entries) {
            val want = proposals[scope.name.lowercase()]?.jsonObject ?: continue
            var n = 0
            val stamps = OpStamps {
                n += 1
                OpStamp("op-propose-$n", propose.text("at"))
            }
            val plan = ConflictProposals.propose(
                state,
                scope,
                conflicts.getValue(scope),
                propose.text("memberId"),
                { "$it-x" },
                stamps,
            ) as Proposal.Ops
            assertEquals(want.getValue("openedItemIds").jsonArray.map { it.jsonPrimitive.content }, plan.openedItemIds)
            val after = TimeFixtures.stateAt(
                state,
                plan.ops.mapIndexed { i, op -> entry(i + 1L, propose.text("memberId"), op) },
                Long.MAX_VALUE,
            )
            val items = BoardJson.json.encodeToJsonElement(
                ListSerializer(Item.serializer()),
                after.partition(scope)!!.items,
            )
            assertEquals(
                CanonicalJson.encode(want.getValue("items")),
                CanonicalJson.encode(items),
                "items after the proposal in $scope",
            )
        }
    }

    private fun entry(seq: Long, memberId: String, op: ScopedOp) = LogEntry(
        seq = seq,
        scope = op.scope,
        memberId = memberId,
        fromDevice = "$memberId-device",
        op = BoardJson.json.encodeToJsonElement(BoardOp.serializer(), op.op).jsonObject,
    )
}

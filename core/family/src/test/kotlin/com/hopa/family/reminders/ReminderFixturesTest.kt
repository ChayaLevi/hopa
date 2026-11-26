package com.hopa.family.reminders

import com.hopa.family.time.FamilyClock
import com.hopa.family.time.TimeFixtures
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * `time-reminders.json`: Homemind's scheduler ticked every minute against this planner run over the
 * same board at the same minutes. Each device plans from the board alone (D5) and must deliver
 * exactly what Homemind's server delivered.
 */
class ReminderFixturesTest {
    private val cases = TimeFixtures.cases("time-reminders.json")

    @Test
    fun `the cases are there`() {
        assertTrue(cases.size >= 12, "reminder cases: ${cases.size}")
    }

    @TestFactory
    fun `every case delivers what Homemind delivered`(): List<DynamicTest> = cases.map { case ->
        DynamicTest.dynamicTest(case.getValue("name").jsonPrimitive.content) { check(case) }
    }

    private fun check(case: JsonObject) {
        val start = TimeFixtures.start(case)
        val log = TimeFixtures.log(case)
        val settings = ReminderSettings(backupContact = case.getValue("backupContact").jsonPrimitive.boolean)
        val presses = case.getValue("snoozes").jsonArray.map { it.jsonObject }
        val snoozes = mutableListOf<LocalSnooze>()
        val delivered = mutableListOf<PlannedAlert>()
        val from = FamilyClock.millis(case.getValue("from").jsonPrimitive.content)
        val to = FamilyClock.millis(case.getValue("to").jsonPrimitive.content)
        var t = from
        while (t <= to) {
            val state = TimeFixtures.stateAt(start, log, t)
            for (press in presses.filter { FamilyClock.millis(it.getValue("at").jsonPrimitive.content) == t }) {
                // The member presses "Remind me later" on the last alert they got for the item.
                val member = press.getValue("memberId").jsonPrimitive.content
                val itemId = press.getValue("itemId").jsonPrimitive.content
                val last = delivered.last { it.recipientId == member && it.itemId == itemId }
                snoozes += LocalSnooze(member, last.reminderKey, FamilyClock.iso(t))
            }
            delivered += ReminderPlanner.plan(state, t, t + ReminderTiming.MINUTE, settings, snoozes)
            t += ReminderTiming.MINUTE
        }
        val want = case.getValue("deliveries").jsonArray.map { row(it.jsonObject) }
        assertEquals(want.joinToString("\n"), delivered.map(::row).sorted().joinToString("\n"))
    }

    private fun row(d: JsonObject): String {
        fun s(field: String) = d.getValue(field).jsonPrimitive.content
        return listOf(
            s("at"), s("recipientId"), s("key"), s("kind"), s("itemId"), s("date"), s("ask"), s("step"), s("tier"),
            d.getValue("itemVersion").jsonPrimitive.int, d.getValue("sensitive").jsonPrimitive.boolean,
            d["recurring"]?.jsonPrimitive?.boolean ?: false,
        ).joinToString(" ")
    }

    private fun row(a: PlannedAlert): String = listOf(
        a.at, a.recipientId, a.key, a.kind.wire, a.itemId, a.date, a.ask.wire, a.step, a.tier.wire,
        a.itemVersion, a.sensitive, a.recurring,
    ).joinToString(" ")
}

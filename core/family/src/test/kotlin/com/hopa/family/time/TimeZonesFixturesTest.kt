package com.hopa.family.time

import com.hopa.family.model.Importance
import com.hopa.family.model.Item
import com.hopa.family.model.ItemStatus
import com.hopa.family.model.ItemType
import com.hopa.family.model.Ownership
import com.hopa.family.model.Recurrence
import com.hopa.family.model.Source
import com.hopa.family.model.SourceKind
import com.hopa.family.reminders.EveningSummary
import com.hopa.family.reminders.QuietHours
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** `time-zones.json`: Homemind's clock rules (JS `Intl`) against `java.time`, across DST changes. */
class TimeZonesFixturesTest {
    private val file = TimeFixtures.file("time-zones.json")

    private fun list(name: String): List<JsonObject> = file.getValue(name).jsonArray.map { it.jsonObject }

    private fun JsonObject.text(field: String) = getValue(field).jsonPrimitive.content

    @Test
    fun `local times map to the same instants, gaps and overlaps included`() {
        val cases = list("zonedToUtc")
        assertTrue(cases.size >= 25)
        for (c in cases) {
            val got = FamilyClock.iso(FamilyClock.zonedToUtc(c.text("date"), c.text("time"), c.text("zone")))
            assertEquals(c.text("utc"), got, "${c.text("zone")} ${c.text("date")} ${c.text("time")}")
        }
    }

    @Test
    fun `instants have the same local parts`() {
        for (c in list("localParts")) {
            val parts = FamilyClock.localParts(FamilyClock.millis(c.text("instant")), c.text("zone"))
            val want = LocalParts(c.text("date"), c.text("time"), c.getValue("weekday").jsonPrimitive.int)
            assertEquals(want, parts, "${c.text("zone")} ${c.text("instant")}")
        }
    }

    @Test
    fun `weekly items keep their local time across a change`() {
        for (c in list("occurrences")) {
            val raw = c.getValue("item").jsonObject
            val recurrence = raw["recurrence"]?.jsonObject?.let { r ->
                Recurrence(
                    freq = "weekly",
                    byWeekday = r.getValue("byWeekday").jsonArray.map { it.jsonPrimitive.int },
                    until = r["until"]?.jsonPrimitive?.content,
                    exceptions = r["exceptions"]?.jsonArray?.map { it.jsonPrimitive.content },
                )
            }
            val item = Item(
                id = "item",
                type = ItemType.EVENT,
                title = "Item",
                start = raw.text("start"),
                end = raw["end"]?.jsonPrimitive?.content,
                forMemberIds = emptyList(),
                ownership = Ownership.None,
                sensitive = false,
                importance = Importance.NORMAL,
                unavailableMemberIds = emptyList(),
                recurrence = recurrence,
                status = if (raw["status"] != null) ItemStatus.CANCELLED else ItemStatus.OPEN,
                source = Source(SourceKind.MANUAL, "test"),
                createdBy = "mom",
                version = 1,
            )
            val got = Occurrences.of(
                item,
                FamilyClock.millis(c.text("from")),
                FamilyClock.millis(c.text("to")),
                c.text("zone"),
            )
            val want = c.getValue("occurrences").jsonArray.map {
                val o = it.jsonObject
                Occurrence("item", o.text("date"), o.text("start"), o["end"]?.jsonPrimitive?.content)
            }
            assertEquals(want, got, c.text("name"))
        }
    }

    @Test
    fun `quiet hours and the evening summary`() {
        for (c in list("quietHours")) {
            val q = c.getValue("quietHours").jsonObject
            val quiet = QuietHours(q.text("from"), q.text("to"))
            val at = FamilyClock.millis(c.text("instant"))
            val inside = c.getValue("inside").jsonPrimitive.booleanOrNull!!
            val label = "${c.text("zone")} ${c.text("instant")} $quiet"
            assertEquals(inside, quiet.contains(at, c.text("zone")), label)
            if (inside) assertEquals(c.text("endsAt"), FamilyClock.iso(quiet.endAfter(at, c.text("zone"))), label)
        }
        for (c in list("summary")) {
            val got = EveningSummary.nextAt(FamilyClock.millis(c.text("instant")), c.text("zone"))
            assertEquals(c.text("summaryAt"), FamilyClock.iso(got), "${c.text("zone")} ${c.text("instant")}")
        }
    }
}

@file:Suppress("ktlint:standard:max-line-length")

package com.hopa.family.rules

import com.hopa.contracts.json.CanonicalJson
import com.hopa.family.board.BoardJson
import com.hopa.family.board.FixtureLogs
import com.hopa.family.model.BoardState
import com.hopa.family.model.LogEntry
import com.hopa.family.namespace.FamilyReducer
import com.hopa.family.namespace.FamilyStep
import com.hopa.family.namespace.Namespaces
import com.hopa.family.time.FamilyClock
import com.hopa.family.time.TimeFixtures
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** `ns-rules.json`: Homemind's applyRuleRequest and nextOccurrence against this port. */
class RuleFixturesTest {
    private val file = TimeFixtures.file("ns-rules.json")
    private val namespaces = Namespaces(listOf(RulesNamespace()))

    private fun JsonObject.text(field: String) = getValue(field).jsonPrimitive.content

    @Test
    fun `every step matches Homemind`() {
        for (case in file.getValue("cases").jsonArray.map { it.jsonObject }) {
            val log = BoardJson.json.decodeFromJsonElement(ListSerializer(LogEntry.serializer()), case.getValue("log"))
            val results = case.getValue("results").jsonArray.map { it.jsonObject }
            val after = case.getValue("after").jsonArray
            var state: BoardState = FixtureLogs.seed
            log.forEachIndexed { i, entry ->
                val got = when (val s = FamilyReducer.apply(state, entry, namespaces)) {
                    is FamilyStep.Applied -> "applied".also { state = s.state }
                    is FamilyStep.Rejected -> "rejected ${s.code.name.lowercase()}"
                    else -> "other $s"
                }
                val want = results[i].text("outcome") + (results[i]["code"]?.let { " ${it.jsonPrimitive.content}" } ?: "")
                assertEquals(want, got, "seq ${entry.seq}")
                val rules = RulesNamespace.of(state.scopes.family.ns?.get(RulesNamespace.NAME))
                assertEquals(
                    CanonicalJson.encode(after[i]),
                    CanonicalJson.encode(
                        RulesNamespace.json.encodeToJsonElement(ListSerializer(Rule.serializer()), rules),
                    ),
                    "rules after seq ${entry.seq}",
                )
            }
        }
    }

    @Test
    fun `the next occurrence is Homemind's`() {
        for (case in file.getValue("nextOccurrence").jsonArray.map { it.jsonObject }) {
            val trigger = RuleTrigger("weekly", case.getValue("weekday").jsonPrimitive.int, case.text("time"), 30)
            val (date, at) = RuleSchedule.nextOccurrence(
                trigger,
                FamilyClock.millis(case.text("now")),
                case.text("timezone"),
            )
            assertEquals(case.text("date") to case.text("at"), date to FamilyClock.iso(at), case.toString())
        }
    }
}

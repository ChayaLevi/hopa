@file:Suppress("ktlint:standard:max-line-length")

package com.hopa.family.preferences

import com.hopa.contracts.json.CanonicalJson
import com.hopa.family.board.BoardJson
import com.hopa.family.board.FixtureLogs
import com.hopa.family.model.BoardOp
import com.hopa.family.model.BoardState
import com.hopa.family.model.ItemPatch
import com.hopa.family.model.ItemType
import com.hopa.family.model.LogEntry
import com.hopa.family.model.NewItem
import com.hopa.family.namespace.FamilyReducer
import com.hopa.family.namespace.FamilyStep
import com.hopa.family.namespace.Namespaces
import com.hopa.family.time.TimeFixtures
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/** `ns-preferences.json` and `logic-preferences.json`: Homemind's preferences.ts against this port. */
class PreferenceFixturesTest {
    private val namespaces = Namespaces(listOf(PreferencesNamespace()))
    private val logic = TimeFixtures.file("logic-preferences.json")

    private fun JsonObject.text(field: String) = getValue(field).jsonPrimitive.content

    private fun strings(o: JsonObject, field: String) = o[field]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

    private fun context(o: JsonObject) = PreferenceContext(
        strings(o, "memberIds"),
        strings(o, "texts"),
        strings(o, "tags"),
    )

    private val preferences: List<Preference> = PreferencesNamespace.json.decodeFromJsonElement(
        ListSerializer(Preference.serializer()),
        logic.getValue("preferences"),
    )

    @TestFactory
    fun `every preferences case matches Homemind step by step`(): List<DynamicTest> =
        TimeFixtures.cases("ns-preferences.json").map { case ->
            DynamicTest.dynamicTest(case.text("name")) { replay(case) }
        }

    private fun replay(case: JsonObject) {
        val log = BoardJson.json.decodeFromJsonElement(ListSerializer(LogEntry.serializer()), case.getValue("log"))
        val results = case.getValue("results").jsonArray.map { it.jsonObject }
        val after = case.getValue("after").jsonArray
        var state: BoardState = FixtureLogs.seed
        log.forEachIndexed { i, entry ->
            val want = results[i]
            val got = when (val s = FamilyReducer.apply(state, entry, namespaces)) {
                is FamilyStep.Applied -> "applied".also { state = s.state }
                is FamilyStep.Rejected -> "rejected ${s.code.name.lowercase()}"
                else -> "other $s"
            }
            val expected = want.text("outcome") + (want["code"]?.let { " ${it.jsonPrimitive.content}" } ?: "")
            assertEquals(expected, got, "${case.text("name")} seq ${entry.seq}")
            val prefs = PreferencesNamespace.of(state.scopes.family.ns?.get(PreferencesNamespace.NAME))
            assertEquals(
                CanonicalJson.encode(after[i]),
                CanonicalJson.encode(
                    PreferencesNamespace.json.encodeToJsonElement(ListSerializer(Preference.serializer()), prefs),
                ),
                "${case.text("name")} preferences after seq ${entry.seq}",
            )
        }
    }

    @Test
    fun `scopes read as Homemind reads them`() {
        for (case in logic.getValue("scopeTerms").jsonArray.map { it.jsonObject }) {
            val (concepts, literals) = PreferenceMatching.scopeTerms(case.text("scope"))
            assertEquals(
                strings(case, "concepts") to strings(case, "literals"),
                concepts to literals,
                case.text("scope"),
            )
        }
    }

    @Test
    fun `preferences apply where Homemind applies them`() {
        for (case in logic.getValue("appliesTo").jsonArray.map { it.jsonObject }) {
            val ctx = context(case.getValue("context").jsonObject)
            assertEquals(
                strings(case, "applies"),
                preferences.filter {
                    PreferenceMatching.appliesTo(it, ctx)
                }.map { it.id },
                case.text("name"),
            )
        }
    }

    @Test
    fun `hard preferences rule out the same things`() {
        for (case in logic.getValue("avoidedTerms").jsonArray.map { it.jsonObject }) {
            val pref = preferences.single { it.id == case.text("id") }
            assertEquals(strings(case, "terms"), PreferenceMatching.avoidedTerms(pref), case.text("id"))
        }
        val contexts = logic.getValue("appliesTo").jsonArray.map {
            it.jsonObject
        }.associate { it.text("name") to context(it.getValue("context").jsonObject) }
        for (case in logic.getValue("checkChoice").jsonArray.map { it.jsonObject }) {
            val got = PreferenceMatching.checkChoice(
                preferences,
                contexts.getValue(case.text("context")),
                strings(case, "choice"),
            )
            val want = case.getValue("blocked").jsonArray.map { it.jsonObject }.map { it.text("id") to it.text("term") }
            assertEquals(
                want,
                got.map { it.preference.id to it.term },
                "${case.text("context")} ${strings(case, "choice")}",
            )
        }
    }

    @Test
    fun `item contexts and blocked board changes match`() {
        val seed = FixtureLogs.seed
        val items = seed.scopes.family.items
        for (case in logic.getValue("itemContexts").jsonArray.map { it.jsonObject }) {
            val item = (items + seed.scopes.parents!!.items).single { it.id == case.text("itemId") }
            val scopeItems = if (item in items) items else seed.scopes.parents!!.items
            val want = context(case.getValue("context").jsonObject)
            assertEquals(
                want,
                PreferenceMatching.itemContext(scopeItems, seed.family.members, item),
                case.text("itemId"),
            )
        }
        for (case in logic.getValue("blockedChange").jsonArray.map { it.jsonObject }) {
            val change = case.getValue("change").jsonObject
            val op: BoardOp = if (change.text("kind") == "create") {
                val i = change.getValue("item").jsonObject
                BoardOp.Create(
                    1,
                    "op-x",
                    "2026-11-11T12:00:00.000Z",
                    NewItem(
                        id = "new-item",
                        type = ItemType.entries.single { it.name.lowercase() == i.text("type") },
                        title = i.text("title"),
                        forMemberIds = strings(i, "forMemberIds").ifEmpty { null },
                        parentEventId = i["parentEventId"]?.jsonPrimitive?.content,
                        start = i["start"]?.jsonPrimitive?.content,
                    ),
                )
            } else {
                val patch = change.getValue("patch").jsonObject
                BoardOp.Edit(
                    1,
                    "op-x",
                    "2026-11-11T12:00:00.000Z",
                    change.text("itemId"),
                    change.getValue("version").jsonPrimitive.long.toInt(),
                    ItemPatch(
                        title = patch["title"]?.jsonPrimitive?.content,
                        start = patch["start"]?.jsonPrimitive?.content,
                    ),
                )
            }
            val want = case.getValue("blocked").jsonArray.map { it.jsonObject }.map { it.text("id") to it.text("term") }
            val got = PreferenceMatching.blockedOp(preferences, items, seed.family.members, op)
            assertEquals(want, got.map { it.preference.id to it.term }, case.text("name"))
        }
    }
}

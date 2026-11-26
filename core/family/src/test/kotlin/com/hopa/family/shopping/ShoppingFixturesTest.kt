package com.hopa.family.shopping

import com.hopa.contracts.json.CanonicalJson
import com.hopa.family.board.BoardJson
import com.hopa.family.board.FixtureLogs
import com.hopa.family.model.BoardState
import com.hopa.family.model.Item
import com.hopa.family.model.LogEntry
import com.hopa.family.namespace.FamilyReducer
import com.hopa.family.namespace.FamilyStep
import com.hopa.family.namespace.Namespaces
import com.hopa.family.time.TimeFixtures
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * `ns-shopping.json`: Homemind's shopping.ts, driven step by step, against the `shopping` capability
 * applied through the family reducer. After every step the lists the members see, and the board,
 * must be the same. The one pinned difference: a run's board task has no Chinese title (v1 ops
 * carry none).
 */
class ShoppingFixturesTest {
    private val namespaces = Namespaces(listOf(ShoppingNamespace()))

    @TestFactory
    fun `every case matches Homemind step by step`(): List<DynamicTest> =
        TimeFixtures.cases("ns-shopping.json").map { case ->
            DynamicTest.dynamicTest(case.getValue("name").jsonPrimitive.content) { check(case) }
        }

    private fun stores(list: List<ShoppingStore>) = CanonicalJson.encode(
        ShoppingNamespace.json.encodeToJsonElement(ListSerializer(ShoppingStore.serializer()), list),
    )

    private fun items(list: List<ShoppingItem>) = CanonicalJson.encode(
        ShoppingNamespace.json.encodeToJsonElement(ListSerializer(ShoppingItem.serializer()), list),
    )

    private fun check(case: JsonObject) {
        val log = BoardJson.json.decodeFromJsonElement(ListSerializer(LogEntry.serializer()), case.getValue("log"))
        val results = case.getValue("results").jsonArray.map { it.jsonObject }
        val stepOps = case.getValue("stepOps").jsonArray.map { ops -> ops.jsonArray.map { it.jsonPrimitive.content } }
        val after = case.getValue("after").jsonArray.map { it.jsonObject }
        var state: BoardState = FixtureLogs.seed
        val byOpId = log.associateBy { it.op.getValue("opId").jsonPrimitive.content }
        val name = case.getValue("name").jsonPrimitive.content
        stepOps.forEachIndexed { step, opIds ->
            for (opId in opIds) {
                val entry = byOpId.getValue(opId)
                val want = results.single { it.getValue("seq").jsonPrimitive.content.toLong() == entry.seq }
                val got = when (val s = FamilyReducer.apply(state, entry, namespaces)) {
                    is FamilyStep.Applied -> "applied".also { state = s.state }
                    is FamilyStep.Rejected -> "rejected ${s.code.name.lowercase()}"
                    else -> "other $s"
                }
                val expected = want.getValue("outcome").jsonPrimitive.content +
                    (want["code"]?.let { " ${it.jsonPrimitive.content}" } ?: "")
                assertEquals(expected, got, "$name step ${step + 1}, seq ${entry.seq} (${entry.op["kind"]})")
            }
            val lists = ShoppingView.of(state.scopes.family)
            val wantLists = after[step].getValue("shopping").jsonObject
            assertEquals(
                CanonicalJson.encode(wantLists.getValue("stores")),
                stores(lists.stores),
                "$name step ${step + 1}: stores",
            )
            assertEquals(
                CanonicalJson.encode(wantLists.getValue("items")),
                items(lists.items),
                "$name step ${step + 1}: items",
            )
            val wantBoard = after[step].getValue("board").jsonArray.map { item ->
                val o = item.jsonObject
                val fromShopping = o["source"]?.jsonObject?.get("kind")?.jsonPrimitive?.content == "shopping"
                if (fromShopping) JsonObject(o - "titleZh") else o
            }
            assertEquals(
                CanonicalJson.encode(JsonArray(wantBoard)),
                CanonicalJson.encode(
                    BoardJson.json.encodeToJsonElement(ListSerializer(Item.serializer()), state.scopes.family.items),
                ),
                "$name step ${step + 1}: board",
            )
        }
    }
}

package com.hopa.catalog.routing

import com.hopa.contracts.json.HopaJson
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppRouterTest {
    @Test
    fun every_routing_fixture_is_reproduced() {
        val document = HopaJson.element.parseToJsonElement(
            resource("/fixtures/routing.json"),
        ).jsonObject
        for (case in document["cases"]!!.jsonArray) {
            val item = case.jsonObject
            val name = item["name"]!!.jsonPrimitive.content
            val targets = item["targets"]!!.jsonArray.map { target ->
                val value = target.jsonObject
                RouteTarget(
                    name = value["name"]!!.jsonPrimitive.content,
                    display = value["display"]!!.jsonPrimitive.content,
                    kind = value["kind"]!!.jsonPrimitive.content,
                    bundle = value["bundle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    domains = value["domains"]!!.jsonArray.map {
                        it.jsonPrimitive.content
                    },
                    aliases = value["aliases"]!!.jsonArray.map {
                        it.jsonPrimitive.content
                    },
                )
            }
            val match = AppRouter.route(item["goal"]!!.jsonPrimitive.content, targets)
            val expect = item["expect"]!!.jsonObject
            val matched = expect["matched"]!!.jsonPrimitive.boolean
            assertEquals(matched, match.target != null, "$name matched")
            if (!matched) {
                continue
            }
            assertEquals(expect["name"]!!.jsonPrimitive.content, match.target?.name, "$name target")
            assertEquals(expect["score"]!!.jsonPrimitive.int, match.score, "$name score")
            assertEquals(expect["term"]!!.jsonPrimitive.content, match.term, "$name term")
        }
    }

    private fun resource(path: String): String =
        requireNotNull(javaClass.getResource(path)) { "missing $path" }.readText()
}

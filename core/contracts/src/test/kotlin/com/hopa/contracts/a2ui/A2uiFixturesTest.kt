package com.hopa.contracts.a2ui

import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.contracts.json.HopaJson
import com.hopa.contracts.wire.Action
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

/** Replays `spec/fixtures/a2ui/` - the cases every HOPA renderer must agree on. */
class A2uiFixturesTest {
    @Test
    fun every_surface_case_is_accepted_or_refused_as_the_fixture_says() {
        val cases = fixture("surfaces.json")["cases"]!!.jsonArray
        assertTrue(cases.size >= 30)
        for (item in cases) {
            val case = item.jsonObject
            val name = case.string("name")
            val outcome = A2uiSession().applyAll(case["messages"]!!.jsonArray.map { it.jsonObject })
            if (case["ok"]!!.jsonPrimitive.boolean) {
                when (case.string("state")) {
                    "ready" -> assertTrue(outcome is A2uiOutcome.Ready, "$name: $outcome")
                    "pending" -> assertTrue(outcome is A2uiOutcome.Pending, "$name: $outcome")
                    "deleted" -> assertTrue(outcome is A2uiOutcome.Deleted, "$name: $outcome")
                    else -> fail("$name: unknown state")
                }
                case["expect"]?.jsonObject?.get("text")?.jsonObject?.forEach { (id, text) ->
                    val surface = (outcome as A2uiOutcome.Ready).surface
                    val component = surface.component(id) as A2uiComponent.Text
                    assertEquals(text.jsonPrimitive.content, surface.text(component.text), name)
                }
            } else {
                val refused = outcome as? A2uiOutcome.Refused ?: fail("$name: expected a refusal, got $outcome")
                val error = case["error"]!!.jsonObject
                assertEquals(error.string("code"), refused.problem.code, "$name: ${refused.problem}")
                assertEquals(error.string("path"), refused.problem.path, "$name: ${refused.problem}")
            }
        }
    }

    @Test
    fun approval_rules_hold_on_top_of_a2ui() {
        for (item in fixture("surfaces.json")["approvalCases"]!!.jsonArray) {
            val case = item.jsonObject
            val name = case.string("name")
            val outcome = A2uiSession().applyAll(case["messages"]!!.jsonArray.map { it.jsonObject })
            val surface = (outcome as? A2uiOutcome.Ready)?.surface ?: fail("$name: not a valid surface: $outcome")
            val problem = ApprovalSurfaces.problem(surface, case["isPayment"]!!.jsonPrimitive.boolean)
            val reason = case["reason"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
            assertEquals(reason, problem, name)
            assertEquals(case["ok"]!!.jsonPrimitive.boolean, problem == null, name)
        }
    }

    @Test
    fun every_reply_case_is_accepted_or_refused_as_the_fixture_says() {
        for (item in fixture("replies.json")["cases"]!!.jsonArray) {
            val case = item.jsonObject
            val name = case.string("name")
            val message = case["message"]!!.jsonObject
            when (val decoded = A2uiCodec.decodeReply(message)) {
                is A2uiResult.Ok -> {
                    assertTrue(case["ok"]!!.jsonPrimitive.boolean, "$name: should be refused")
                    assertEquals(message, A2uiCodec.encodeReply(decoded.value), "$name: round trip")
                }
                is A2uiResult.Refused -> {
                    val error = case["error"]?.jsonObject ?: fail("$name: refused ${decoded.problem}")
                    assertEquals(error.string("code"), decoded.problem.code, name)
                    assertEquals(error.string("path"), decoded.problem.path, name)
                }
            }
        }
    }

    @Test
    fun an_answer_is_allow_only_when_the_tap_agrees() {
        for (item in fixture("replies.json")["approvalAnswers"]!!.jsonArray) {
            val case = item.jsonObject
            val allowed = ApprovalSurfaces.allowed(
                allow = case["allow"]!!.jsonPrimitive.boolean,
                action = case["action"]?.takeUnless { it is JsonNull }?.let { action(it) },
                surfaceId = case.string("surfaceId"),
            )
            assertEquals(case["allowed"]!!.jsonPrimitive.boolean, allowed, case.string("name"))
        }
    }

    @Test
    fun a_received_card_is_shown_only_when_it_shows_the_request() {
        val cases = fixture("surfaces.json")["receivedCases"]!!.jsonArray
        assertTrue(cases.size >= 20)
        for (item in cases) {
            val case = item.jsonObject
            val name = case.string("name")
            val card = ApprovalSurfaces.receive(case["a2ui"]!!, request(case["request"]!!.jsonObject))
            if (case["shown"]!!.jsonPrimitive.boolean) {
                assertTrue(card is ApprovalCard.Shown, "$name: $card")
            } else {
                val refused = card as? ApprovalCard.Refused ?: fail("$name: expected a refusal, got $card")
                assertEquals(case.string("reason"), refused.reason, name)
            }
        }
    }

    @Test
    fun a_wire_answer_is_allow_only_when_its_action_agrees() {
        for (item in fixture("replies.json")["wireAnswers"]!!.jsonArray) {
            val case = item.jsonObject
            val allowed = ApprovalSurfaces.answered(
                allow = case["allow"]!!.jsonPrimitive.boolean,
                action = case["action"],
                surfaceId = case.string("surfaceId"),
            )
            assertEquals(case["allowed"]!!.jsonPrimitive.boolean, allowed, case.string("name"))
        }
    }

    @Test
    fun a_choice_is_read_only_from_its_own_button() {
        for (item in fixture("replies.json")["choiceAnswers"]!!.jsonArray) {
            val case = item.jsonObject
            val chosen = ChoiceSurfaces.chosen(
                action(case["action"]!!),
                case.string("surfaceId"),
                case["options"]!!.jsonArray.map { it.jsonPrimitive.content },
            )
            val expected = case["chosen"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
            assertEquals(expected, chosen, case.string("name"))
        }
    }

    private fun action(body: JsonElement): A2uiAction {
        val message =
            JsonObject(mapOf("version" to kotlinx.serialization.json.JsonPrimitive(A2ui.VERSION), "action" to body))
        return (A2uiCodec.decodeReply(message) as A2uiResult.Ok).value as A2uiAction
    }

    private fun request(json: JsonObject) = ApprovalRequest(
        title = json.string("title"),
        body = json.string("body"),
        amount = json.string("amount"),
        merchant = json.string("merchant"),
        consequence = json.string("consequence"),
        isPayment = json["isPayment"]!!.jsonPrimitive.boolean,
        action = Action(kind = json["action"]!!.jsonObject.string("kind")),
    )

    private fun JsonObject.string(key: String) = this[key]!!.jsonPrimitive.content

    private fun fixture(name: String): JsonObject = HopaJson.element.parseToJsonElement(
        requireNotNull(javaClass.getResource("/fixtures/a2ui/$name")) { "missing $name" }.readText(),
    ).jsonObject
}

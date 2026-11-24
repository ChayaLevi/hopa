package com.hopa.contracts.a2ui

import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.contracts.json.HopaJson
import com.hopa.contracts.wire.Action
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** The builders against the goldens in `spec/fixtures/a2ui/builders.json`. */
class A2uiBuildersTest {
    private val golden = HopaJson.element.parseToJsonElement(
        requireNotNull(javaClass.getResource("/fixtures/a2ui/builders.json")).readText(),
    ).jsonObject

    @Test
    fun approval_cards_match_the_goldens_and_pass_every_rule() {
        for (item in golden["approvals"]!!.jsonArray) {
            val case = item.jsonObject
            val name = case["name"]!!.jsonPrimitive.content
            val request = request(case["request"]!!.jsonObject)
            val surfaceId = case["surfaceId"]!!.jsonPrimitive.content
            val built = ApprovalSurfaces.build(request, surfaceId).map(A2uiCodec::encode)
            assertEquals(case["messages"]!!.jsonArray.map { it.jsonObject }, built, name)

            val surface = ready(built, name)
            assertNull(ApprovalSurfaces.problem(surface, request.isPayment), name)
            val allow = surface.tap(ApprovalSurfaces.ALLOW_ID, Instant.parse("2026-11-22T10:15:00Z"))!!
            assertTrue(ApprovalSurfaces.allowed(true, allow, surfaceId), name)
            val deny = surface.tap(ApprovalSurfaces.DENY_ID, Instant.parse("2026-11-22T10:15:00Z"))!!
            assertEquals(false, ApprovalSurfaces.allowed(true, deny, surfaceId), name)
        }
    }

    @Test
    fun a_payment_card_shows_the_request_values() {
        val request = ApprovalRequest(
            title = "Confirm payment",
            body = "Money may leave your account.",
            amount = "12.40",
            merchant = "Example Shop",
            consequence = "HOPA cannot undo a payment.",
            isPayment = true,
            action = Action(kind = "tap"),
        )
        val surface = ready(ApprovalSurfaces.build(request, "appr_t_1").map(A2uiCodec::encode), "payment")
        val notice = surface.components.values.filterIsInstance<A2uiComponent.PaymentNotice>().single()
        assertEquals("12.40", surface.text(notice.amount))
        assertEquals("Example Shop", surface.text(notice.merchant!!))
        assertEquals("HOPA cannot undo a payment.", surface.text(notice.consequence!!))
        assertEquals(ApprovalSurfaces.ACKNOWLEDGE, surface.text(notice.acknowledge))
    }

    @Test
    fun choice_cards_match_the_goldens_and_a_tap_reads_back() {
        for (item in golden["choices"]!!.jsonArray) {
            val case = item.jsonObject
            val name = case["name"]!!.jsonPrimitive.content
            val options = case["options"]!!.jsonArray.map { it.jsonPrimitive.content }
            val surfaceId = case["surfaceId"]!!.jsonPrimitive.content
            val built = ChoiceSurfaces.build(case["question"]!!.jsonPrimitive.content, options, surfaceId)
                .map(A2uiCodec::encode)
            assertEquals(case["messages"]!!.jsonArray.map { it.jsonObject }, built, name)

            val surface = ready(built, name)
            options.forEachIndexed { index, option ->
                val tap = surface.tap("option$index", Instant.EPOCH)!!
                assertEquals(option, ChoiceSurfaces.chosen(tap, surfaceId, options), name)
            }
        }
    }

    @Test
    fun surface_ids_are_safe_for_any_task_id() {
        assertEquals("appr_tsk_01h_2_1", ApprovalSurfaces.surfaceId("tsk-01h-2", 1))
        assertEquals("ask_turn_7_3", ChoiceSurfaces.surfaceId("turn 7", 3))
        assertTrue(A2ui.SURFACE_ID.matches(ApprovalSurfaces.surfaceId("x".repeat(500), 99)))
    }

    private fun ready(messages: List<JsonObject>, name: String): A2uiSurface {
        val outcome = A2uiSession().applyAll(messages)
        return (outcome as? A2uiOutcome.Ready)?.surface ?: error("$name: $outcome")
    }

    private fun request(json: JsonObject) = ApprovalRequest(
        title = json["title"]!!.jsonPrimitive.content,
        body = json["body"]!!.jsonPrimitive.content,
        amount = json["amount"]!!.jsonPrimitive.content,
        merchant = json["merchant"]!!.jsonPrimitive.content,
        consequence = json["consequence"]!!.jsonPrimitive.content,
        isPayment = json["isPayment"]!!.jsonPrimitive.boolean,
        action = Action(kind = json["action"]!!.jsonObject["kind"]!!.jsonPrimitive.content),
    )
}

package com.hopa.contracts.a2ui

import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.contracts.wire.Action
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Session behaviour the fixtures cannot show compactly: limits and transactions. */
class A2uiSessionTest {
    private val request = ApprovalRequest(
        title = "Open Settings?",
        body = "HOPA wants to open Wi-Fi settings.",
        amount = "",
        merchant = "",
        consequence = "",
        isPayment = false,
        action = Action(kind = "launch"),
    )

    @Test
    fun a_refused_update_leaves_the_last_good_surface() {
        val session = A2uiSession()
        val first = session.apply(ApprovalSurfaces.build(request, "s1").single())
        assertTrue(first is A2uiOutcome.Ready)
        val removeTitle = A2uiMessage.UpdateDataModel("s1", JsonNull, "/approval/title")
        val refused = session.apply(removeTitle)
        assertTrue(refused is A2uiOutcome.Refused)
        val surface = session.surface("s1")!!
        assertEquals("Open Settings?", surface.text((surface.component("title") as A2uiComponent.Text).text))
    }

    @Test
    fun more_than_sixty_four_components_are_refused() {
        val texts = (0 until 64).map { A2uiComponent.Text("t$it", A2uiText.Literal("x")) }
        val message = A2uiMessage.CreateSurface(
            "big",
            listOf(A2uiComponent.Column(A2uiSurface.ROOT, listOf("t0"))) + texts,
        )
        val outcome = A2uiSession().apply(A2uiCodec.encode(message))
        assertEquals(A2uiCode.VALIDATION_FAILED, (outcome as A2uiOutcome.Refused).problem.code)
        assertEquals("/createSurface/components", outcome.problem.path)
    }

    @Test
    fun a_tree_deeper_than_eight_is_refused() {
        val columns = (0 until 9).map { level ->
            val id = if (level == 0) A2uiSurface.ROOT else "c$level"
            A2uiComponent.Column(id, listOf(if (level == 8) "leaf" else "c${level + 1}"))
        }
        val message = A2uiMessage.CreateSurface("deep", columns + A2uiComponent.Text("leaf", A2uiText.Literal("x")))
        val outcome = A2uiSession().apply(message)
        assertEquals(A2uiCode.VALIDATION_FAILED, (outcome as A2uiOutcome.Refused).problem.code)
    }

    @Test
    fun text_longer_than_the_limit_is_refused() {
        val long = "x".repeat(A2ui.MAX_TEXT + 1)
        val json = A2uiCodec.encode(
            A2uiMessage.CreateSurface("long", listOf(A2uiComponent.Text(A2uiSurface.ROOT, A2uiText.Literal(long)))),
        )
        val outcome = A2uiSession().apply(json)
        assertEquals("/createSurface/components/0/text", (outcome as A2uiOutcome.Refused).problem.path)
    }

    @Test
    fun a_refusal_becomes_an_a2ui_error_message() {
        val outcome = A2uiSession().apply(
            buildJsonObject {
                put("version", "v1.0")
                put("deleteSurface", buildJsonObject { put("surfaceId", "s9") })
            },
        ) as A2uiOutcome.Refused
        assertEquals("s9", outcome.surfaceId)
        val error = A2uiCodec.encodeReply(A2uiCodec.errorFor("s9", outcome.problem))
        assertEquals(
            JsonPrimitive(A2uiCode.UNSUPPORTED),
            (error["error"] as kotlinx.serialization.json.JsonObject)["code"],
        )
        assertTrue(A2uiCodec.decodeReply(error) is A2uiResult.Ok)
    }

    @Test
    fun data_pointers_read_and_write_like_rfc_6901() {
        val model = buildJsonObject { put("a/b", buildJsonObject { put("c~d", "v") }) }
        assertEquals(JsonPrimitive("v"), JsonPointer.get(model, "/a~1b/c~0d"))
        val written = JsonPointer.set(model, "/x/y", JsonPrimitive("z"))!!
        assertEquals(JsonPrimitive("z"), JsonPointer.get(written, "/x/y"))
        assertEquals(null, JsonPointer.get(JsonPointer.set(written, "/x/y", JsonNull)!!, "/x/y"))
    }
}

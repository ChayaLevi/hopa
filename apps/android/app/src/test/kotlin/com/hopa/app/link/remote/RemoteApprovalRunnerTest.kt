package com.hopa.app.link.remote

import com.hopa.approval.ApprovalAnswer
import com.hopa.backend.link.BackendLink
import com.hopa.backend.link.BackendState
import com.hopa.backend.wire.BackendEnvelope
import com.hopa.contracts.a2ui.A2uiCodec
import com.hopa.contracts.a2ui.A2uiSurface
import com.hopa.contracts.a2ui.ApprovalSurfaces
import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.contracts.json.HopaJson
import com.hopa.contracts.wire.Action
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class RemoteApprovalRunnerTest {
    private val request = ApprovalRequest(
        title = "Confirm payment",
        body = "Money may leave your account.",
        amount = "12.40",
        merchant = "Example Shop",
        consequence = "HOPA cannot undo a payment.",
        isPayment = true,
        action = Action(kind = "payment.pay", text = "12.40"),
    )
    private val surfaceId = ApprovalSurfaces.surfaceId("ap-1", 1)

    @Test
    fun `an approval request is acked and the answer is a task result`() = runBlocking {
        val link = RecordingLink()
        val asker = Asker { _, _ -> ApprovalAnswer(true) }

        runner(link, asker).handle(task())

        assertEquals(1, asker.asked)
        assertNull(asker.card, "no a2ui means the phone builds its own card")
        assertEquals("task.ack", link.frames.first().type)
        val result = link.frames.last()
        assertEquals("task.result", result.type)
        assertEquals("dev-pc", result.to)
        assertEquals("t-1", result.task)
        assertTrue(approval(result)["allow"]!!.jsonPrimitive.boolean)
        assertNull(approval(result)["action"], "a request without a card is answered without a tap")
    }

    @Test
    fun `a refusal is sent as a result with allow false`() = runBlocking {
        val link = RecordingLink()

        runner(link, Asker { _, _ -> ApprovalAnswer.DENY }).handle(task())

        val result = link.frames.last()
        assertEquals("task.result", result.type)
        assertFalse(approval(result)["allow"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun `a sent card is drawn and its approve tap travels back`() = runBlocking {
        val link = RecordingLink()
        val asker = Asker { _, card -> ApprovalAnswer(true, card!!.tap(ApprovalSurfaces.ALLOW_ID, Instant.EPOCH)) }

        runner(link, asker).handle(task(ApprovalSurfaces.wire(request, surfaceId)))

        assertEquals(surfaceId, asker.card?.id, "the sheet draws the card that was sent")
        val answer = approval(link.frames.last())
        assertTrue(answer["allow"]!!.jsonPrimitive.boolean)
        val action = answer["action"]!!.jsonObject
        assertTrue(ApprovalSurfaces.answered(true, action, surfaceId), "the PC's own check accepts it")
    }

    @Test
    fun `with a card, allow without the approve tap is deny`() = runBlocking {
        val link = RecordingLink()

        runner(link, Asker { _, _ -> ApprovalAnswer(true) }).handle(task(ApprovalSurfaces.wire(request, surfaceId)))

        val answer = approval(link.frames.last())
        assertFalse(answer["allow"]!!.jsonPrimitive.boolean)
        assertNull(answer["action"])
    }

    @Test
    fun `a card that does not show the request is never drawn and is denied`() = runBlocking {
        val link = RecordingLink()
        val asker = Asker { _, _ -> ApprovalAnswer(true) }
        val card = ApprovalSurfaces.wire(request.copy(amount = "1240.00"), surfaceId)

        runner(link, asker).handle(task(card))

        assertEquals(0, asker.asked, "nobody is asked about a card that lies")
        val answer = approval(link.frames.last())
        assertFalse(answer["allow"]!!.jsonPrimitive.boolean)
        val error = answer["error"]!!.jsonObject["error"]!!.jsonObject
        assertEquals("VALIDATION_FAILED", error["code"]!!.jsonPrimitive.content)
        assertEquals(surfaceId, error["surfaceId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a card that is not valid A2UI is denied with its error`() = runBlocking {
        val link = RecordingLink()
        val asker = Asker { _, _ -> ApprovalAnswer(true) }
        val wrongVersion = ApprovalSurfaces.wire(request, surfaceId).map {
            JsonObject(it.jsonObject + ("version" to JsonPrimitive("0.9")))
        }

        runner(link, asker).handle(task(JsonArray(wrongVersion)))

        assertEquals(0, asker.asked)
        val answer = approval(link.frames.last())
        assertFalse(answer["allow"]!!.jsonPrimitive.boolean)
        assertNotNull(A2uiCodec.decodeReply(answer["error"]!!.jsonObject))
        assertEquals("UNSUPPORTED", answer["error"]!!.jsonObject["error"]!!.jsonObject["code"]!!.jsonPrimitive.content)
    }

    private fun runner(link: RecordingLink, asker: Asker) = RemoteApprovalRunner(
        deviceId = { "dev-phone" },
        link = { link },
        ask = asker::ask,
        foreground = { true },
        postApproval = {},
        timeoutMillis = 2_000,
    )

    private fun approval(frame: BackendEnvelope) = frame.body["approval"]!!.jsonObject

    private fun task(a2ui: JsonElement? = null): BackendEnvelope = BackendEnvelope(
        id = "m-1",
        type = "task.needs_approval",
        ts = 1,
        from = "dev-pc",
        to = "dev-phone",
        task = "t-1",
        body = buildJsonObject {
            put("approval", HopaJson.element.encodeToJsonElement(ApprovalRequest.serializer(), request))
            a2ui?.let { put("a2ui", it) }
        },
    )

    private class Asker(private val answer: (ApprovalRequest, A2uiSurface?) -> ApprovalAnswer) {
        var asked = 0
        var card: A2uiSurface? = null

        suspend fun ask(request: ApprovalRequest, card: A2uiSurface?): ApprovalAnswer {
            asked += 1
            this.card = card
            return answer(request, card)
        }
    }

    private class RecordingLink : BackendLink {
        val frames = mutableListOf<BackendEnvelope>()
        override val state = MutableStateFlow<BackendState>(
            BackendState.Live(deviceId = "dev-phone", queued = 0, peers = listOf("dev-pc")),
        )
        override var onTask: (suspend (BackendEnvelope) -> Unit)? = null
        override var onApproval: (suspend (BackendEnvelope) -> Unit)? = null

        override fun start(scope: CoroutineScope) = Unit
        override suspend fun stop() = Unit
        override suspend fun send(frame: BackendEnvelope) {
            frames += frame
        }

        override suspend fun ack(task: BackendEnvelope) {
            frames += task.copy(id = "ack-${task.task}", type = "task.ack")
        }

        override suspend fun ping() = Unit
    }
}

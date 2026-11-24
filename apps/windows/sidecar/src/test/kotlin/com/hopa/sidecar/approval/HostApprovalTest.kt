package com.hopa.sidecar.approval

import com.hopa.contracts.a2ui.A2uiCodec
import com.hopa.contracts.a2ui.A2uiSurface
import com.hopa.contracts.a2ui.ApprovalCard
import com.hopa.contracts.a2ui.ApprovalSurfaces
import com.hopa.contracts.approval.ApprovalDecision
import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.contracts.wire.Action
import com.hopa.sidecar.protocol.Stdio
import com.hopa.sidecar.store.PendingApprovals
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** ADR-0027 on the PC: the question carries its card, and a phone tap must agree with it. */
class HostApprovalTest {
    private val request = ApprovalRequest(
        title = "Confirm payment",
        body = "Money may leave your account.",
        amount = "100",
        merchant = "Example Shop",
        consequence = "HOPA cannot undo a payment.",
        isPayment = true,
        action = Action(kind = "payment.pay", text = "100"),
    )
    private val out = ByteArrayOutputStream()
    private lateinit var original: PrintStream

    @BeforeEach
    fun capture() {
        original = System.out
        System.setOut(PrintStream(out, true, Charsets.UTF_8))
    }

    @AfterEach
    fun restore() {
        System.setOut(original)
    }

    @Test
    fun the_request_carries_a_card_that_shows_the_typed_request() {
        val (message, card) = ask { _, _ -> null }.second
        assertEquals("approval.request", message["type"]!!.jsonPrimitive.content)
        assertEquals(request.amount, message["amount"]!!.jsonPrimitive.content, "the window keeps its typed fields")
        assertEquals(true, card.id.startsWith("appr_ap_1_"), card.id)
    }

    @Test
    fun the_phone_approve_tap_allows() {
        val decision = ask { card, _ -> tap(card, ApprovalSurfaces.ALLOW_ID) }.first
        assertEquals(ApprovalDecision(true, "phone"), decision)
    }

    @Test
    fun allow_with_a_deny_tap_is_deny() {
        val decision = ask { card, _ -> tap(card, ApprovalSurfaces.DENY_ID) }.first
        assertEquals(ApprovalDecision(false, "phone"), decision)
    }

    @Test
    fun allow_with_a_tap_on_another_surface_is_deny() {
        val decision = ask { card, _ -> tap(card.copy(id = "appr_ap_1_old_1"), ApprovalSurfaces.ALLOW_ID) }.first
        assertEquals(ApprovalDecision(false, "phone"), decision)
    }

    @Test
    fun allow_with_a_malformed_tap_is_deny() {
        val decision = ask { _, _ -> Json.parseToJsonElement("""{"version":"1.0","action":{"name":"approve"}}""") }
            .first
        assertEquals(ApprovalDecision(false, "phone"), decision)
    }

    @Test
    fun a_phone_without_a2ui_keeps_the_old_answer() {
        val decision = ask { _, _ -> null }.first
        assertEquals(ApprovalDecision(true, "phone"), decision)
    }

    /** Asks once and answers allow from the phone with the tap [answer] makes from the card that was sent. */
    private fun ask(
        answer: (A2uiSurface, JsonObject) -> JsonElement?,
    ): Pair<ApprovalDecision, Pair<JsonObject, A2uiSurface>> = runBlocking {
        val dir = Files.createTempDirectory("host-approval")
        val approval = HostApproval(Stdio(), PendingApprovals(dir.resolve("approvals.json")), timeoutMillis = 5_000)
        val decision = async { approval.decide(request) }
        var line: String? = null
        while (line == null) {
            yield()
            line = out.toString(Charsets.UTF_8).lineSequence().firstOrNull { "approval.request" in it }
        }
        val message = Json.parseToJsonElement(line).jsonObject
        val card = (ApprovalSurfaces.receive(message["a2ui"]!!, request) as ApprovalCard.Shown).surface
        approval.resolve(message["id"]!!.jsonPrimitive.content, true, "phone", answer(card, message))
        decision.await() to (message to card)
    }

    private fun tap(card: A2uiSurface, button: String): JsonElement =
        A2uiCodec.encodeReply(card.tap(button, Instant.EPOCH)!!)
}

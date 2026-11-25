package com.hopa.sidecar.approval

import com.hopa.backend.approval.AnswerSigning
import com.hopa.backend.approval.ApprovalChallenge
import com.hopa.backend.approval.SignedAnswer
import com.hopa.contracts.a2ui.A2uiCodec
import com.hopa.contracts.a2ui.A2uiSurface
import com.hopa.contracts.a2ui.ApprovalCard
import com.hopa.contracts.a2ui.ApprovalSurfaces
import com.hopa.contracts.approval.ApprovalDecision
import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.contracts.wire.Action
import com.hopa.sidecar.protocol.Stdio
import com.hopa.sidecar.store.PendingApprovals
import com.hopa.sidecar.store.PhoneKeys
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The PC side of ADR-0027 and ADR-0028: each question carries its card and a
 * fresh challenge, and a phone allow counts only with this card's approve tap
 * and a signature over this question from the phone's pinned key.
 */
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
    private val phoneSeed = ByteArray(32) { it.toByte() }
    private val otherSeed = ByteArray(32) { (it + 32).toByte() }
    private val out = ByteArrayOutputStream()
    private lateinit var original: PrintStream
    private lateinit var dir: Path
    private lateinit var pins: PhoneKeys

    /** What a phone sees: the typed request, the checked card and the challenge. */
    private class Asked(val id: String, val card: A2uiSurface, val challenge: ApprovalChallenge, val message: JsonObject)

    @BeforeEach
    fun setUp() {
        original = System.out
        System.setOut(PrintStream(out, true, Charsets.UTF_8))
        dir = Files.createTempDirectory("host-approval")
        pins = PhoneKeys(dir.resolve("phone-keys.json"))
    }

    @AfterEach
    fun restore() {
        System.setOut(original)
    }

    @Test
    fun the_request_relays_a_card_and_a_challenge_and_keeps_its_typed_fields() {
        val (_, asked) = ask { null }
        assertEquals("approval.request", asked.message["type"]!!.jsonPrimitive.content)
        assertEquals(request.amount, asked.message["amount"]!!.jsonPrimitive.content, "the window keeps its fields")
        assertTrue(asked.card.id.startsWith("appr_ap_1_"), asked.card.id)
        assertTrue(asked.challenge.expiresAt > System.currentTimeMillis())
    }

    @Test
    fun a_first_signed_allow_pins_the_phone_key_and_the_next_one_verifies() {
        assertEquals(ApprovalDecision(true, "phone"), ask { phone(it, signed(phoneSeed, it)) }.first)
        assertNotNull(pins.pinned("dev-phone"))
        assertEquals(ApprovalDecision(true, "phone"), ask { phone(it, signed(phoneSeed, it)) }.first)
    }

    @Test
    fun a_signature_from_another_key_after_the_pin_is_deny() {
        ask { phone(it, signed(phoneSeed, it)) }
        assertEquals(ApprovalDecision(false, "phone"), ask { phone(it, signed(otherSeed, it)) }.first)
    }

    @Test
    fun an_unsigned_allow_after_the_pin_is_deny() {
        ask { phone(it, signed(phoneSeed, it)) }
        assertEquals(ApprovalDecision(false, "phone"), ask { phone(it, signature = null) }.first)
    }

    @Test
    fun a_signature_over_another_amount_is_deny() {
        val decision = ask { phone(it, signed(phoneSeed, it, request.copy(amount = "1"))) }.first
        assertEquals(ApprovalDecision(false, "phone"), decision)
        assertNull(pins.pinned("dev-phone"), "a refused signature pins nothing")
    }

    @Test
    fun a_signature_over_another_question_is_deny() {
        val replay = ApprovalChallenge.fresh(expiresAt = Long.MAX_VALUE)
        val decision = ask { phone(it, SignedAnswer.sign(phoneSeed, replay, it.card.id, request, true).toJson()) }.first
        assertEquals(ApprovalDecision(false, "phone"), decision)
    }

    @Test
    fun a_late_answer_is_deny_even_when_signed() {
        var clock = 1_000L
        val decision = ask(now = { clock }) { asked ->
            clock = asked.challenge.expiresAt + 1
            phone(asked, signed(phoneSeed, asked))
        }.first
        assertEquals(ApprovalDecision(false, "phone"), decision)
    }

    @Test
    fun a_deny_tap_is_deny_whatever_the_signature() {
        val decision = ask { phone(it, signed(phoneSeed, it), button = ApprovalSurfaces.DENY_ID) }.first
        assertEquals(ApprovalDecision(false, "phone"), decision)
    }

    @Test
    fun a_phone_from_before_signing_keeps_the_old_answer() {
        assertEquals(ApprovalDecision(true, "phone"), ask { phone(it, signature = null) }.first)
        assertEquals(ApprovalDecision(true, "phone"), ask { null }.first, "no tap and no signature: before A2UI too")
    }

    @Test
    fun the_person_at_the_pc_needs_no_signature() {
        ask { phone(it, signed(phoneSeed, it)) }
        assertEquals(ApprovalDecision(true, "pc"), ask(device = "pc") { null }.first)
    }

    @Test
    fun forgetting_the_keys_lets_a_phone_paired_again_pin_its_new_key() {
        ask { phone(it, signed(phoneSeed, it)) }
        assertEquals(1, pins.forgetAll())
        assertEquals(ApprovalDecision(true, "phone"), ask { phone(it, signed(otherSeed, it)) }.first)
    }

    @Test
    fun an_unreadable_key_file_denies_every_phone_allow() {
        val file = dir.resolve("broken-keys.json")
        Files.writeString(file, "{not json")
        pins = PhoneKeys(file)
        assertEquals(ApprovalDecision(false, "phone"), ask { phone(it, signed(phoneSeed, it)) }.first)
        assertEquals(ApprovalDecision(false, "phone"), ask { phone(it, signature = null) }.first)
    }

    /** Asks once; the phone answers allow with what [answer] builds, or the window answers when it is null. */
    private fun ask(
        device: String = "phone",
        now: () -> Long = System::currentTimeMillis,
        answer: (Asked) -> JsonObject?,
    ): Pair<ApprovalDecision, Asked> = runBlocking {
        out.reset()
        val approval = HostApproval(
            Stdio(),
            PendingApprovals(dir.resolve("approvals.json")),
            pins,
            timeoutMillis = 5_000,
            now = now,
        )
        val decision = async { approval.decide(request) }
        var line: String? = null
        while (line == null) {
            yield()
            line = out.toString(Charsets.UTF_8).lineSequence().firstOrNull { "approval.request" in it }
        }
        val message = Json.parseToJsonElement(line).jsonObject
        val relay = message["relay"]!!.jsonObject
        val card = (ApprovalSurfaces.receive(relay["a2ui"]!!, request) as ApprovalCard.Shown).surface
        val asked = Asked(
            message["id"]!!.jsonPrimitive.content,
            card,
            ApprovalChallenge.parse(relay["challenge"])!!,
            message,
        )
        approval.resolve(asked.id, true, device, answer(asked))
        decision.await() to asked
    }

    private fun phone(asked: Asked, signature: JsonElement?, button: String = ApprovalSurfaces.ALLOW_ID) =
        buildJsonObject {
            put("from", "dev-phone")
            put(
                "approval",
                buildJsonObject {
                    put("allow", true)
                    put("action", A2uiCodec.encodeReply(asked.card.tap(button, Instant.EPOCH)!!))
                    signature?.let { put("signature", it) }
                },
            )
        }

    private fun signed(seed: ByteArray, asked: Asked, what: ApprovalRequest = request): JsonElement =
        SignedAnswer.sign(seed, asked.challenge, asked.card.id, what, allow = true).toJson()
}

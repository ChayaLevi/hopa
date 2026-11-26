package com.hopa.backend.approval

import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.contracts.json.HopaJson
import com.hopa.contracts.wire.Action
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Replays `spec/fixtures/approval-signatures.json`, written independently in Python. */
class SignedAnswerFixturesTest {
    private val fixture = HopaJson.element.parseToJsonElement(
        requireNotNull(javaClass.getResource("/fixtures/approval-signatures.json")).readText(),
    ).jsonObject

    @Test
    fun every_vector_is_reproduced_byte_for_byte() {
        for (item in fixture["vectors"]!!.jsonArray) {
            val case = item.jsonObject
            val name = case.text("name")
            val seed = AnswerSignature.decode(case.text("seed"))!!
            val request = request(case["request"]!!.jsonObject)
            val challenge = ApprovalChallenge.parse(case["challenge"])!!
            val surfaceId = case.optText("surfaceId")
            val allow = case["allow"]!!.jsonPrimitive.boolean

            assertEquals(case.text("publicKey"), AnswerSignature.encode(AnswerSigning.publicKey(seed)), name)
            assertEquals(case.text("fingerprint"), AnswerSigning.fingerprint(AnswerSigning.publicKey(seed)), name)
            assertEquals(case.text("actionHash"), SignedAnswer.actionHash(request), name)
            assertEquals(case.text("message"), hex(SignedAnswer.message(challenge, surfaceId, request, allow)), name)
            val signed = SignedAnswer.sign(seed, challenge, surfaceId, request, allow)
            assertEquals(case["signature"], signed.toJson(), "$name: Ed25519 is deterministic")
            assertTrue(SignedAnswer.verify(signed, challenge, surfaceId, request, allow), name)
            assertFalse(SignedAnswer.verify(signed, challenge, surfaceId, request, !allow), "$name: other decision")
        }
    }

    @Test
    fun every_answer_is_read_as_the_fixture_says() {
        for (item in fixture["checks"]!!.jsonArray) {
            val case = item.jsonObject
            val name = case.text("name")
            val pins = MemoryPins()
            case.optText("pinned")?.let { pins.pin(case.text("from"), AnswerSignature.decode(it)!!) }
            val before = pins.pinned(case.text("from"))?.copyOf()

            val verdict = AnswerCheck.check(
                allow = case["allow"]!!.jsonPrimitive.boolean,
                signature = case["signature"],
                from = case.text("from"),
                challenge = ApprovalChallenge.parse(case["challenge"])!!,
                surfaceId = case.optText("surfaceId"),
                request = request(case["request"]!!.jsonObject),
                now = case["now"]!!.jsonPrimitive.long,
                pins = pins,
            )

            assertEquals(case.text("code"), verdict.code, name)
            assertEquals(case["allowed"]!!.jsonPrimitive.boolean, verdict is AnswerVerdict.Allowed, name)
            if (case["pins"]!!.jsonPrimitive.boolean) {
                assertNull(before, name)
                assertArrayEquals(AnswerSignature.parse(case["signature"])!!.key, pins.pinned(case.text("from")), name)
            } else if (before != null) {
                assertArrayEquals(before, pins.pinned(case.text("from")), "$name: a pin never changes")
            }
        }
    }

    @Test
    fun a_challenge_or_signature_that_is_not_well_formed_is_not_read() {
        val good = ApprovalChallenge.fresh(expiresAt = 1)
        assertEquals(good, ApprovalChallenge.parse(good.toJson()))
        assertNull(ApprovalChallenge.parse(JsonObject(good.toJson() - "nonce")))
        assertNull(ApprovalChallenge.parse(JsonObject(good.toJson() + ("v" to JsonPrimitive(2)))))
        assertNull(ApprovalChallenge.parse(JsonNull))
        assertNull(AnswerSignature.parse(JsonObject(emptyMap())))
    }

    private class MemoryPins : KeyPins {
        private val keys = mutableMapOf<String, ByteArray>()

        override fun pinned(deviceId: String): ByteArray? = keys[deviceId]

        override fun pin(deviceId: String, key: ByteArray) {
            keys[deviceId] = key.copyOf()
        }
    }

    private fun request(json: JsonObject) = ApprovalRequest(
        title = json.text("title"),
        body = json.text("body"),
        amount = json.text("amount"),
        merchant = json.text("merchant"),
        consequence = json.text("consequence"),
        isPayment = json["isPayment"]!!.jsonPrimitive.boolean,
        action = Action(kind = json["action"]!!.jsonObject.text("kind")),
    )

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content

    private fun JsonObject.optText(key: String) = this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
}

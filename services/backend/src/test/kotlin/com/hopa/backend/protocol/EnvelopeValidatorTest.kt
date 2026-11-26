package com.hopa.backend.protocol

import java.util.Base64
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnvelopeValidatorTest {
    private fun envelope() = Envelope(
        v = 1,
        id = UUID.randomUUID().toString(),
        type = "task.create",
        ts = 1,
        to = "dev-pc",
        body = JsonObject(emptyMap()),
    )

    @Test
    fun `a frame over the cap is refused`() {
        val envelope = envelope()
        val raw = GatewayCodec.encode(
            envelope.copy(body = JsonObject(mapOf("blob" to kotlinx.serialization.json.JsonPrimitive("a".repeat(70_000))))),
        )

        val error = EnvelopeValidator.validate(raw, envelope)

        assertEquals("E_SCHEMA", error?.code)
    }

    @Test
    fun `an unknown major version is refused`() {
        val envelope = envelope().copy(v = 2)

        val error = EnvelopeValidator.validate(GatewayCodec.encode(envelope), envelope)

        assertEquals("E_VERSION", error?.code)
    }

    @Test
    fun `an unknown type is refused`() {
        val envelope = envelope().copy(type = "device.reboot")

        val error = EnvelopeValidator.validate(GatewayCodec.encode(envelope), envelope)

        assertEquals("E_UNSUPPORTED", error?.code)
    }

    @Test
    fun `a blank id is refused`() {
        val envelope = envelope().copy(id = " ")

        val error = EnvelopeValidator.validate(GatewayCodec.encode(envelope), envelope)

        assertEquals("E_SCHEMA", error?.code)
    }

    @Test
    fun `an invalid trace is replaced, not rejected`() {
        val envelope = envelope().copy(trace = "not-a-traceparent")

        assertNull(EnvelopeValidator.validate(GatewayCodec.encode(envelope), envelope))
        assertTrue(
            EnvelopeValidator.traceOf(envelope)
                .matches(Regex("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$")),
        )
    }

    @Test
    fun `a valid trace is preserved`() {
        val trace = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"

        assertEquals(trace, EnvelopeValidator.traceOf(envelope().copy(trace = trace)))
    }

    @Test
    fun `a task message without a target is refused`() {
        val envelope = envelope().copy(to = null)

        val error = EnvelopeValidator.validate(GatewayCodec.encode(envelope), envelope)

        assertEquals("E_SCHEMA", error?.code)
    }

    @Test
    fun `a non-uuid id is refused`() {
        val envelope = envelope().copy(id = "x")

        val error = EnvelopeValidator.validate(GatewayCodec.encode(envelope), envelope)

        assertEquals("E_SCHEMA", error?.code)
    }

    @Test
    fun `every schema-invalid fixture names the validator code`() {
        val raw = requireNotNull(javaClass.getResource("/fixtures/gateway-v0.json")).readText()
        val cases = Json.parseToJsonElement(raw).jsonObject["cases"]!!.jsonArray
        var checked = 0
        for (element in cases) {
            val case = element.jsonObject
            val schemaValid = case["schema"]?.jsonPrimitive?.boolean
                ?: case["ok"]!!.jsonPrimitive.boolean
            val expected = case["error"]?.jsonPrimitive?.content
            if (schemaValid ||
                expected == null ||
                expected !in setOf("E_SCHEMA", "E_VERSION", "E_UNSUPPORTED")
            ) {
                continue
            }
            checked++
            val message = case["message"]!!.jsonObject.toString()
            val actual = when (val decoded = GatewayCodec.decode(message)) {
                is DecodeResult.Failed -> decoded.error.code
                is DecodeResult.Ok -> EnvelopeValidator.validate(message, decoded.envelope)?.code
            }
            assertEquals(expected, actual, case["name"]!!.jsonPrimitive.content)
        }
        assertTrue(checked >= 4, "expected at least four validator-level fixtures")
    }

    @Test
    fun `every board fixture gets the validator answer it names`() {
        val raw = requireNotNull(javaClass.getResource("/fixtures/gateway-board.json")).readText()
        val cases = Json.parseToJsonElement(raw).jsonObject["cases"]!!.jsonArray
        var refused = 0
        for (element in cases) {
            val case = element.jsonObject
            val name = case["name"]!!.jsonPrimitive.content
            val schemaValid = case["schema"]?.jsonPrimitive?.boolean
                ?: case["ok"]!!.jsonPrimitive.boolean
            val message = case["message"]!!.jsonObject.toString()
            val actual = when (val decoded = GatewayCodec.decode(message)) {
                is DecodeResult.Failed -> decoded.error.code
                is DecodeResult.Ok -> EnvelopeValidator.validate(message, decoded.envelope)?.code
            }
            if (schemaValid) {
                // A conflict or a scope refusal depends on stored state: the
                // frame itself is well formed and must pass the validator.
                assertNull(actual, name)
            } else {
                refused++
                assertEquals(case["error"]!!.jsonPrimitive.content, actual, name)
            }
        }
        assertTrue(refused >= 4, "expected at least four schema-level board refusals")
    }

    @Test
    fun `a board op at the ciphertext bounds passes and one byte over is refused`() {
        fun op(rawBytes: Int): Envelope = envelope().copy(
            type = "board.op",
            to = null,
            body = buildJsonObject {
                put("scope", JsonPrimitive("family"))
                put("keyEpoch", JsonPrimitive(0))
                put(
                    "ct",
                    JsonPrimitive(
                        Base64.getEncoder().encodeToString(ByteArray(rawBytes)),
                    ),
                )
            },
        )
        val max = EnvelopeValidator.MAX_BOARD_CIPHERTEXT_BYTES
        val min = EnvelopeValidator.MIN_BOARD_CIPHERTEXT_BYTES
        val largest = op(max)
        val largestRaw = GatewayCodec.encode(largest)

        assertTrue(largestRaw.toByteArray().size < EnvelopeValidator.MAX_FRAME_BYTES, "a full op fits the frame cap")
        assertNull(EnvelopeValidator.validate(largestRaw, largest))
        assertNull(EnvelopeValidator.validate(GatewayCodec.encode(op(min)), op(min)))
        assertEquals("E_SCHEMA", EnvelopeValidator.validate(GatewayCodec.encode(op(max + 1)), op(max + 1))?.code)
        assertEquals("E_SCHEMA", EnvelopeValidator.validate(GatewayCodec.encode(op(min - 1)), op(min - 1))?.code)
    }

    @Test
    fun `a board op needs no target device`() {
        val envelope = envelope().copy(
            type = "board.op",
            to = null,
            body = Json.parseToJsonElement(
                """{"scope":"parents","keyEpoch":1,"ct":"${Base64.getEncoder().encodeToString(ByteArray(40))}"}""",
            ).jsonObject,
        )

        assertNull(EnvelopeValidator.validate(GatewayCodec.encode(envelope), envelope))
    }
}

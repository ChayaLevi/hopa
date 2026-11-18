package com.hopa.backend.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private data class FixtureCase(
    val name: String,
    val accepted: Boolean,
    val message: String,
)

class GatewayCodecTest {
    private fun cases(): List<FixtureCase> {
        val raw = requireNotNull(javaClass.getResource("/fixtures/gateway-v0.json")) {
            "fixtures are not on the test classpath"
        }.readText()
        return Json.parseToJsonElement(raw).jsonObject["cases"]!!.jsonArray.map { element ->
            val case = element.jsonObject
            FixtureCase(
                name = case["name"]!!.jsonPrimitive.content,
                accepted = case["ok"]!!.jsonPrimitive.boolean,
                message = case["message"]!!.jsonObject.toString(),
            )
        }
    }

    @Test
    fun `every accepted fixture decodes and re-decodes identically`() {
        val accepted = cases().filter { it.accepted }
        assertTrue(accepted.size >= 10, "expected at least ten accepted vectors")

        for (case in accepted) {
            val first = GatewayCodec.decode(case.message)
            assertTrue(first is DecodeResult.Ok, case.name)

            val envelope = (first as DecodeResult.Ok).envelope
            assertTrue(envelope.id.isNotEmpty(), case.name)

            val again = GatewayCodec.decode(GatewayCodec.encode(envelope))
            assertEquals(envelope, (again as DecodeResult.Ok).envelope, case.name)
        }
    }

    @Test
    fun `a malformed message is a schema failure, not an exception`() {
        val result = GatewayCodec.decode("{not json")

        assertTrue(result is DecodeResult.Failed)
        assertEquals("E_SCHEMA", (result as DecodeResult.Failed).error.code)
    }

    @Test
    fun `unknown fields are dropped on re-encode`() {
        val raw = """
            {"v":1,"id":"018f3a5e-6b1c-7c3a-9d2e-4f5a6b7c8d9e","type":"task.create",
             "ts":1,"to":"dev-1","household":"hh_evil","body":{}}
        """.trimIndent()

        val envelope = (GatewayCodec.decode(raw) as DecodeResult.Ok).envelope

        assertFalse(GatewayCodec.encode(envelope).contains("household"))
        assertEquals("dev-1", envelope.to)
    }
}

package com.hopa.backend.board.client

import com.hopa.backend.wire.BackendEnvelope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class BoardLinkTest {
    private class FakeHttp(val answers: MutableMap<String, HttpAnswer>) : BoardHttp {
        val calls = mutableListOf<String>()

        override suspend fun get(path: String): HttpAnswer = answer("GET $path")

        override suspend fun post(path: String, json: String): HttpAnswer = answer("POST $path $json")

        override suspend fun put(path: String, json: String): HttpAnswer = answer("PUT $path $json")

        private fun answer(call: String): HttpAnswer {
            calls += call
            return answers.entries.firstOrNull { call.startsWith(it.key) }?.value ?: HttpAnswer(500, "")
        }
    }

    private fun page(hasMore: Boolean, vararg ops: String) =
        HttpAnswer(200, """{"scope":"family","hasMore":$hasMore,"ops":[${ops.joinToString(",")}]}""")

    private fun frame(type: String, body: kotlinx.serialization.json.JsonObject) =
        BackendEnvelope(id = "srv-$type", type = type, ts = 1, body = body)

    @Test
    fun `an append sends a board op and returns the ack`() = runBlocking {
        val sent = mutableListOf<BackendEnvelope>()
        lateinit var link: BoardLink
        link = BoardLink(FakeHttp(mutableMapOf()), send = { sent += it }, clock = { 5 })
        val result = async { link.append("parents", "op-1", 1, byteArrayOf(1, 2, 3)) }
        while (sent.isEmpty()) yield()
        assertEquals("board.op", sent.single().type)
        assertEquals("op-1", sent.single().id)
        assertEquals(1, sent.single().body.getValue("keyEpoch").jsonPrimitive.int)
        assertArrayEquals(
            byteArrayOf(1, 2, 3),
            Base64.getDecoder().decode(sent.single().body.getValue("ct").jsonPrimitive.content),
        )
        link.receive(
            frame(
                "board.op.ack",
                buildJsonObject {
                    put("scope", "parents")
                    put("seq", 42)
                    put("duplicate", false)
                },
            ),
        )
        assertEquals(AppendResult.Acked("parents", 42, duplicate = false), result.await())
    }

    @Test
    fun `an error frame answers the op in flight`() = runBlocking {
        val sent = mutableListOf<BackendEnvelope>()
        val link = BoardLink(FakeHttp(mutableMapOf()), send = { sent += it })
        val result = async { link.append("parents", "op-1", 1, byteArrayOf(1)) }
        while (sent.isEmpty()) yield()
        link.receive(
            frame(
                "error",
                buildJsonObject {
                    put("code", "E_SCOPE")
                    put("retryable", false)
                },
            ),
        )
        assertEquals(AppendResult.Refused("E_SCOPE", retryable = false), result.await())
    }

    @Test
    fun `board changed is passed on`() = runBlocking {
        val changes = mutableListOf<Pair<String, Long>>()
        val link = BoardLink(FakeHttp(mutableMapOf()), send = {})
        link.onChanged = { scope, seq -> changes += scope to seq }
        link.receive(
            frame(
                "board.changed",
                buildJsonObject {
                    put("scope", "family")
                    put("seq", 7)
                },
            ),
        )
        assertEquals(listOf("family" to 7L), changes)
    }

    @Test
    fun `pull follows pages and reads every field`() = runBlocking {
        val ct = Base64.getEncoder().encodeToString(byteArrayOf(9, 9))
        fun op(seq: Int) = """{"seq":$seq,"opId":"op-$seq","fromDevice":"d1","memberId":"m1",""" +
            """"at":"2026-11-11T12:00:00.000Z","keyEpoch":0,"ct":"$ct"}"""
        val http = FakeHttp(
            mutableMapOf(
                "GET /v1/board/ops?scope=family&after=0&" to page(true, op(1), op(3)),
                "GET /v1/board/ops?scope=family&after=3&" to page(false, op(5)),
            ),
        )
        val ops = BoardLink(http, send = {}).pullAll("family", 0)
        assertEquals(listOf(1L, 3L, 5L), ops.map { it.seq })
        assertEquals("m1", ops.first().memberId)
        assertArrayEquals(byteArrayOf(9, 9), ops.first().ct)
    }

    @Test
    fun `scope key answers`() = runBlocking {
        val wrapped = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(60) { 1 })
        val held = BoardLink(
            FakeHttp(
                mutableMapOf(
                    "GET /v1/scope-keys/parents" to HttpAnswer(
                        200,
                        """{"scope":"parents","keyEpoch":2,"wrappedKey":"$wrapped",""" +
                            """"wrapperDeviceId":null,"wrapperPublicKey":null}""",
                    ),
                ),
            ),
            send = {},
        ).scopeKey("parents")
        assertEquals(2, (held as ScopeKeyAnswer.Held).keyEpoch)
        val missing = BoardLink(
            FakeHttp(
                mutableMapOf(
                    "GET /v1/scope-keys/parents" to HttpAnswer(
                        404,
                        """{"code":"E_TENANT","message":"no wrap","currentEpoch":null,""" +
                            """"bootstrapEpoch":2,"lostEpoch":1}""",
                    ),
                ),
            ),
            send = {},
        ).scopeKey("parents")
        assertEquals(ScopeKeyAnswer.Missing(currentEpoch = null, bootstrapEpoch = 2, lostEpoch = 1), missing)
    }

    @Test
    fun `cursor and wrap requests carry their bodies`() = runBlocking {
        val http =
            FakeHttp(
                mutableMapOf(
                    "PUT /v1/board/cursor" to HttpAnswer(204, ""),
                    "POST /v1/scope-keys/parents" to HttpAnswer(201, ""),
                ),
            )
        val link = BoardLink(http, send = {})
        link.putCursor("family", 12)
        link.putScopeKey("parents", "dad-laptop", ByteArray(60), 1)
        assertTrue(http.calls[0].contains(""""scope":"family","seq":12"""))
        assertTrue(http.calls[1].contains(""""deviceId":"dad-laptop""""))
    }
}

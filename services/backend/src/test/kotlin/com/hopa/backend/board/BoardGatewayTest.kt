package com.hopa.backend.board

import com.hopa.backend.ServerConfig
import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.board.BoardFrames.online
import com.hopa.backend.board.BoardFrames.op
import com.hopa.backend.harness.FakeDevice
import com.hopa.backend.harness.FakeFcm
import com.hopa.backend.harness.MapAuth
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.harness.bodyString
import com.hopa.backend.module
import com.hopa.backend.protocol.DecodeResult
import com.hopa.backend.protocol.Envelope
import com.hopa.backend.protocol.GatewayCodec
import com.hopa.backend.push.PushPort
import com.hopa.backend.store.PgBoardStore
import com.hopa.backend.store.PgDevices
import com.hopa.backend.trace.PgTraceStore
import com.hopa.backend.trace.TraceRecorder
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `board.op` through the real gateway, on Postgres (B3.2-B3.5): the ack, the
 * error codes, `board.changed` to the devices that may read the scope, board
 * wakes for the offline ones, and the trace of one op.
 */
class BoardGatewayTest {
    private val db = PostgresHarness.appDatabase()
    private val store = PgBoardStore(db)
    private val devices = PgDevices(db)

    /** Parent, parent, teen; both parents hold the parents key at epoch 1. */
    private fun household(): List<DeviceIdentity> {
        val family = BoardHouseholds.seed(listOf("parent", "parent", "teen"))
        family.take(2).forEach { store.putWrap(it.householdId, BoardScopes.PARENTS, 1, it.deviceId, ByteArray(60), null) }
        return family
    }

    private fun config(family: List<DeviceIdentity>, push: PushPort = PushPort.None, traces: PgTraceStore? = null) =
        ServerConfig(
            version = "0.1.0",
            auth = MapAuth(family.associateBy { "tok-${it.deviceId}" }),
            devices = devices,
            boardStore = store,
            push = push,
            traces = traces ?: TraceRecorder.None,
            traceQuery = traces,
        )

    private fun pushToken(device: DeviceIdentity, token: String) {
        devices.touch(device.deviceId, device.householdId, token, System.currentTimeMillis())
    }

    @Test
    fun `the sender gets an ack with a seq and the other device gets board changed`() = testApplication {
        val (mom, dad) = household()
        application { module(config(listOf(mom, dad))) }
        val http = createClient { install(WebSockets) }
        val momSocket = online(http, mom.deviceId)
        val dadSocket = online(http, dad.deviceId)

        momSocket.send(op(BoardScopes.FAMILY))
        val ack = momSocket.expect("board.op.ack")
        assertEquals("family", ack.bodyString("scope"))
        assertEquals("1", ack.bodyString("seq"))
        assertEquals("false", ack.bodyString("duplicate"))
        assertEquals(mom.deviceId, ack.to)

        val changed = dadSocket.expect("board.changed")
        assertEquals("family", changed.bodyString("scope"))
        assertEquals("1", changed.bodyString("seq"))
        assertEquals(dad.deviceId, changed.to)
        momSocket.expectNothing("board.changed")
    }

    @Test
    fun `a parents op reaches the other parent, never the teen or the sender`() = testApplication {
        val family = household()
        val (mom, dad, teen) = family
        application { module(config(family)) }
        val http = createClient { install(WebSockets) }
        val momSocket = online(http, mom.deviceId)
        val dadSocket = online(http, dad.deviceId)
        val teenSocket = online(http, teen.deviceId)

        momSocket.send(op(BoardScopes.PARENTS))
        assertEquals("1", momSocket.expect("board.op.ack").bodyString("seq"))
        assertEquals("parents", dadSocket.expect("board.changed").bodyString("scope"))
        teenSocket.expectNothing("board.changed")
        momSocket.expectNothing("board.changed")

        // The teen still hears about family ops, with the shared seq.
        momSocket.send(op(BoardScopes.FAMILY))
        momSocket.expect("board.op.ack")
        assertEquals("2", teenSocket.expect("board.changed").bodyString("seq"))
    }

    @Test
    fun `a teen device cannot append to parents`() = testApplication {
        val family = household()
        val teen = family[2]
        application { module(config(family)) }
        val http = createClient { install(WebSockets) }
        val momSocket = online(http, family[0].deviceId)
        val teenSocket = online(http, teen.deviceId)

        teenSocket.send(op(BoardScopes.PARENTS))

        assertEquals("E_SCOPE", teenSocket.expect("error").bodyString("code"))
        momSocket.expectNothing("board.changed")
        assertEquals(emptyMap<String, Long>(), store.head(teen.householdId))
    }

    @Test
    fun `a duplicate is acked with the stored seq and tells nobody`() = testApplication {
        val (mom, dad) = household()
        application { module(config(listOf(mom, dad))) }
        val http = createClient { install(WebSockets) }
        val momSocket = online(http, mom.deviceId)
        val dadSocket = online(http, dad.deviceId)
        val first = op(BoardScopes.FAMILY)

        momSocket.send(first)
        momSocket.expect("board.op.ack")
        dadSocket.expect("board.changed")
        momSocket.send(first)

        val again = momSocket.expect("board.op.ack")
        assertEquals("1", again.bodyString("seq"))
        assertEquals("true", again.bodyString("duplicate"))
        dadSocket.expectNothing("board.changed")
    }

    @Test
    fun `the board fixtures replay through the gateway`() = testApplication {
        val (mom, dad) = household()
        application { module(config(listOf(mom, dad))) }
        val http = createClient { install(WebSockets) }
        val momSocket = online(http, mom.deviceId)
        val raw = requireNotNull(javaClass.getResource("/fixtures/gateway-board.json")).readText()
        val cases = Json.parseToJsonElement(raw).jsonObject["cases"]!!.jsonArray.map { it.jsonObject }
        val seqByOp = mutableMapOf<String, String>()
        var replayed = 0

        for (case in cases) {
            val name = case["name"]!!.jsonPrimitive.content
            if (case["direction"]?.jsonPrimitive?.content == "server") continue
            val message = case["message"]!!.jsonObject.toString()
            val id = case["message"]!!.jsonObject["id"]!!.jsonPrimitive.content
            momSocket.sendFixture(message)
            replayed++
            if (case["ok"]!!.jsonPrimitive.boolean) {
                val ack = momSocket.expect("board.op.ack")
                val seq = ack.bodyString("seq")!!
                val earlier = seqByOp[id]
                if (earlier != null) {
                    assertEquals("true", ack.bodyString("duplicate"), name)
                    assertEquals(earlier, seq, name)
                } else {
                    assertEquals("false", ack.bodyString("duplicate"), name)
                    seqByOp[id] = seq
                }
            } else {
                val error = momSocket.expect("error")
                assertEquals(case["error"]!!.jsonPrimitive.content, error.bodyString("code"), name)
            }
        }
        assertTrue(replayed >= 8, "expected the device-sent board cases to be replayed")
        assertEquals(mapOf("family" to 1L, "parents" to 2L), store.head(mom.householdId))
    }

    @Test
    fun `server-authored board frames are refused from a device`() = testApplication {
        val (mom, dad) = household()
        application { module(config(listOf(mom, dad))) }
        val http = createClient { install(WebSockets) }
        val momSocket = online(http, mom.deviceId)

        momSocket.send(
            Envelope(
                v = 1,
                id = UUID.randomUUID().toString(),
                type = "board.changed",
                ts = 1,
                to = dad.deviceId,
                body = buildJsonObject {
                    put("scope", "family")
                    put("seq", 9)
                },
            ),
        )

        assertEquals("E_UNSUPPORTED", momSocket.expect("error").bodyString("code"))
    }

    @Test
    fun `an offline device that may read the scope gets a board wake, a teen does not`() = testApplication {
        FakeFcm().use { fcm ->
            val family = household()
            val (mom, dad, teen) = family
            pushToken(dad, "fcm-dad")
            pushToken(teen, "fcm-teen")
            application { module(config(family, push = fcm.push())) }
            val http = createClient { install(WebSockets) }
            val momSocket = online(http, mom.deviceId)

            momSocket.send(op(BoardScopes.PARENTS))
            momSocket.expect("board.op.ack")

            assertEquals(1, fcm.requests.size)
            assertTrue(fcm.requests.single().contains("\"fcm-dad\""))
            assertEquals(
                mapOf("type" to "board.changed", "scope" to "parents", "seq" to "1"),
                fcm.dataOf().single(),
            )

            momSocket.send(op(BoardScopes.FAMILY))
            momSocket.expect("board.op.ack")
            // The family op wakes the teen; dad's wake is coalesced into the
            // window his parents wake opened.
            assertEquals(2, fcm.requests.size)
            assertTrue(fcm.requests.last().contains("\"fcm-teen\""))
        }
    }

    @Test
    fun `a burst of board ops wakes once with the latest seq and leaves the task wakes alone`() = testApplication {
        FakeFcm().use { fcm ->
            val clock = AtomicLong(1_763_300_000_000)
            val (mom, dad) = household()
            pushToken(dad, "fcm-dad")
            val push = fcm.push(clock = clock::get)
            application { module(config(listOf(mom, dad), push = push)) }
            val http = createClient { install(WebSockets) }
            val momSocket = online(http, mom.deviceId)

            repeat(10) {
                momSocket.send(op(BoardScopes.PARENTS))
                momSocket.expect("board.op.ack")
            }
            assertEquals(listOf("1"), fcm.dataOf().map { it["seq"] })

            clock.addAndGet(11_000)
            assertEquals(1, push.flushBoardWakes())
            assertEquals(listOf("1", "10"), fcm.dataOf().map { it["seq"] })

            // The task-wake budget (six a minute) is untouched by the board burst.
            repeat(6) {
                momSocket.send(
                    Envelope(
                        v = 1,
                        id = UUID.randomUUID().toString(),
                        type = "task.create",
                        ts = 1,
                        to = dad.deviceId,
                        body = buildJsonObject { put("goal", "g") },
                    ),
                )
                momSocket.expect("task.ack")
            }
            assertEquals(6, fcm.dataOf().count { it.containsKey("taskId") })
        }
    }

    @Test
    fun `one op shows its append and fan-out spans in its trace`() = testApplication {
        val (mom, dad) = household()
        val traces = PgTraceStore(db)
        application { module(config(listOf(mom, dad), traces = traces)) }
        val http = createClient { install(WebSockets) }
        val momSocket = online(http, mom.deviceId)
        online(http, dad.deviceId)
        val traceId = UUID.randomUUID().toString().replace("-", "")

        momSocket.send(op(BoardScopes.FAMILY, trace = "00-$traceId-00f067aa0ba902b7-01"))
        val ack = momSocket.expect("board.op.ack")
        assertEquals("00-$traceId-00f067aa0ba902b7-01", ack.trace)

        val response = client.get("/v1/traces/$traceId") { header("Authorization", "Bearer tok-${mom.deviceId}") }
        val operations = Json.parseToJsonElement(response.bodyAsText()).jsonObject["spans"]!!.jsonArray
            .map { it.jsonObject["operation"]!!.jsonPrimitive.content }
        assertTrue("board.append" in operations, "spans: $operations")
        assertTrue("board.fanout" in operations, "spans: $operations")
    }
}

/** Sends a fixture message as the envelope it decodes to. */
private suspend fun FakeDevice.sendFixture(text: String) {
    val decoded = GatewayCodec.decode(text)
    require(decoded is DecodeResult.Ok) { "fixture does not decode" }
    send(decoded.envelope)
}

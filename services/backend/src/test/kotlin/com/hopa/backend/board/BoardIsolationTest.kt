package com.hopa.backend.board

import com.hopa.backend.ServerConfig
import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.board.BoardFrames.online
import com.hopa.backend.board.client.BoardCipher
import com.hopa.backend.harness.MapAuth
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.http.RateLimiter
import com.hopa.backend.log.EventLog
import com.hopa.backend.module
import com.hopa.backend.protocol.Envelope
import com.hopa.backend.store.PgBoardStore
import com.hopa.backend.store.PgDevices
import com.hopa.backend.trace.PgTraceStore
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.random.Random
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Isolation and blindness (B4.6). A thousand random attempts by household A's
 * devices to reach household B through every new route and `board.op` are all
 * refused or stay inside A; B is unchanged afterwards. Then a dump of every
 * board table, the trace spans and the event log holds no trace of the
 * plaintext the ops were sealed from.
 */
class BoardIsolationTest {
    private val db = PostgresHarness.appDatabase()
    private val store = PgBoardStore(db)
    private val service = BoardService(store)
    private val marker = "HOPA-PLAINTEXT-MARKER-${UUID.randomUUID()}"

    private class Sealed(val opId: String, val scope: String, val ct: ByteArray)

    /** Seals [count] ops carrying the marker into [device]'s household, under real keys. */
    private fun appendSealed(device: DeviceIdentity, scope: String, key: ByteArray, count: Int): List<Sealed> =
        List(count) { index ->
            val opId = UUID.randomUUID().toString()
            val epoch = if (scope == BoardScopes.FAMILY) 0 else 1
            val ct = BoardCipher.seal(
                key,
                BoardCipher.Place(device.householdId, scope, opId, epoch),
                "$marker:$scope:$index".toByteArray(),
            )
            check(service.append(device, opId, scope, epoch, ct) is BoardResult.Ok)
            Sealed(opId, scope, ct)
        }

    private fun snapshot(household: String, devices: List<DeviceIdentity>): String = buildString {
        append(store.head(household))
        BoardScopes.ALL.forEach { scope ->
            store.opsAfter(household, scope, 0, 10_000).forEach { append("|${it.seq}:${it.opId}:${it.fromDevice}:${it.body.contentHashCode()}") }
        }
        devices.forEach { device ->
            append("|cursor:${store.cursors(household, device.deviceId)}")
            append("|wrap:${store.scopeKeyWrap(household, device.deviceId, BoardScopes.PARENTS)?.wrappedKey?.contentHashCode()}")
        }
    }

    @Test
    fun `a thousand cross-household attempts are refused or stay home, and nothing stored reads as plaintext`() = testApplication {
        val a = BoardHouseholds.seed(listOf("parent", "parent", "teen"))
        val b = BoardHouseholds.seed(listOf("parent", "parent"))
        val keyA = Random.nextBytes(32)
        val keyB = Random.nextBytes(32)
        val householdKeyA = Random.nextBytes(32)
        val householdKeyB = Random.nextBytes(32)
        a.take(2).forEach { store.putWrap(it.householdId, BoardScopes.PARENTS, 1, it.deviceId, Random.nextBytes(60), a[0].deviceId) }
        b.forEach { store.putWrap(it.householdId, BoardScopes.PARENTS, 1, it.deviceId, Random.nextBytes(60), b[0].deviceId) }
        val sealedB = appendSealed(b[0], BoardScopes.FAMILY, householdKeyB, 5) + appendSealed(b[1], BoardScopes.PARENTS, keyB, 5)
        appendSealed(a[0], BoardScopes.FAMILY, householdKeyA, 3)
        appendSealed(a[1], BoardScopes.PARENTS, keyA, 3)
        store.putCursor(b[0].householdId, b[0].deviceId, BoardScopes.FAMILY, 4)
        val bBefore = snapshot(b[0].householdId, b)
        val bSecrets = (b.flatMap { listOf(it.deviceId, it.memberId, it.householdId) } + sealedB.map { it.opId }).toSet()

        val events = CopyOnWriteArrayList<String>()
        val traces = PgTraceStore(db)
        application {
            module(
                ServerConfig(
                    version = "0.1.0",
                    auth = MapAuth((a + b).associateBy { "tok-${it.deviceId}" }),
                    devices = PgDevices(db),
                    boardStore = store,
                    traces = traces,
                    traceQuery = traces,
                    messageLimit = RateLimiter(1_000_000, 1_000),
                    log = EventLog { name, fields -> events += "$name $fields" },
                ),
            )
        }
        val http = createClient { install(WebSockets) }
        val sockets = a.associate { it.deviceId to online(http, it.deviceId) }
        val random = Random(2026)
        val refusedPosts = mutableListOf<HttpStatusCode>()
        // B's op ids that A re-sent as its own ops: from then on they are A's
        // rows too, so seeing them in A's answers is not a leak.
        val planted = mutableSetOf<String>()
        fun assertNoLeak(attempt: Int, body: String) =
            (bSecrets - planted).forEach { assertFalse(body.contains(it), "attempt $attempt leaked $it") }

        repeat(1_000) { attempt ->
            val caller = a[random.nextInt(a.size)]
            val auth = "Bearer tok-${caller.deviceId}"
            val target = b[random.nextInt(b.size)]
            val scope = BoardScopes.ALL[random.nextInt(2)]
            when (random.nextInt(6)) {
                0 -> {
                    val body = client.get("/v1/board/ops?scope=$scope&after=${random.nextInt(4)}") { header("Authorization", auth) }.bodyAsText()
                    assertNoLeak(attempt, body)
                }

                1 -> {
                    val body = client.get("/v1/board/head") { header("Authorization", auth) }.bodyAsText()
                    assertNoLeak(attempt, body)
                }

                2 -> client.put("/v1/board/cursor") {
                    header("Authorization", auth)
                    contentType(ContentType.Application.Json)
                    // Extra fields naming B are ignored; the cursor lands on the caller.
                    setBody("""{"scope":"$scope","seq":${random.nextInt(50)},"deviceId":"${target.deviceId}","householdId":"${target.householdId}"}""")
                }

                3 -> {
                    val body = client.get("/v1/scope-keys/parents") { header("Authorization", auth) }.bodyAsText()
                    assertNoLeak(attempt, body)
                }

                4 -> {
                    val response = client.post("/v1/scope-keys/parents") {
                        header("Authorization", auth)
                        contentType(ContentType.Application.Json)
                        setBody("""{"deviceId":"${target.deviceId}","wrappedKey":"${Base64.getEncoder().encodeToString(Random.nextBytes(60))}","keyEpoch":1}""")
                    }
                    refusedPosts += response.status
                    assertTrue(response.status.value in 400..499, "attempt $attempt: ${response.status}")
                }

                else -> {
                    // B's op id and ciphertext, with fields naming B: the op can only land in A.
                    val stolen = sealedB[random.nextInt(sealedB.size)]
                    val socket = sockets.getValue(caller.deviceId)
                    planted += stolen.opId
                    socket.send(
                        Envelope(
                            v = 1,
                            id = stolen.opId,
                            type = "board.op",
                            ts = 1,
                            to = target.deviceId,
                            body = buildJsonObject {
                                put("scope", BoardScopes.FAMILY)
                                put("ct", Base64.getEncoder().encodeToString(stolen.ct))
                                put("keyEpoch", 0)
                                put("household", target.householdId)
                            },
                        ),
                    )
                    socket.expect("board.op.ack")
                }
            }
        }

        assertEquals(bBefore, snapshot(b[0].householdId, b), "household B changed")
        assertTrue(refusedPosts.isNotEmpty() && refusedPosts.all { it == HttpStatusCode.NotFound }, "every foreign wrap is E_TENANT")

        // Blindness: dump every board table, the spans and the events.
        val markerBytes = marker.toByteArray()
        val needles = listOf(
            marker,
            markerBytes.joinToString("") { "%02x".format(it) },
            Base64.getEncoder().encodeToString(markerBytes).dropLast(4),
        )
        val dump = PostgresHarness.adminDatabase().read { connection ->
            listOf("board_heads", "board_ops", "board_cursors", "scope_key_wraps", "trace_spans").joinToString("\n") { table ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("select row_to_json(t)::text from $table t").use { rows ->
                        buildString { while (rows.next()) appendLine(rows.getString(1)) }
                    }
                }
            }
        }
        assertTrue(dump.contains(sealedB.first().opId), "the dump covers the board rows")
        needles.forEach { needle ->
            assertFalse(dump.contains(needle, ignoreCase = true), "a table holds the plaintext marker")
            assertFalse(events.any { it.contains(needle, ignoreCase = true) }, "the event log holds the plaintext marker")
        }
    }
}

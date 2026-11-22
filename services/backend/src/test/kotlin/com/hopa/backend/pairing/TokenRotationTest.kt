package com.hopa.backend.pairing

import com.hopa.backend.ServerConfig
import com.hopa.backend.gateway.DeviceRegistry
import com.hopa.backend.harness.FakeDevice
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.harness.bodyString
import com.hopa.backend.module
import com.hopa.backend.protocol.Envelope
import com.hopa.backend.store.PgDevices
import com.hopa.backend.store.PostgresProbe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TokenRotationTest {
    private val db = PostgresHarness.appDatabase()
    private val clock = AtomicLong(1_763_212_800_000)
    private lateinit var auth: PgAuth
    private lateinit var devices: PgDevices
    private lateinit var registry: DeviceRegistry
    private lateinit var tokens: TokenIssuer
    private lateinit var pairing: PairingService

    @BeforeEach
    fun setUp() {
        auth = PgAuth(db, clock = clock::get)
        devices = PgDevices(db)
        registry = DeviceRegistry()
        tokens = TokenIssuer(
            db,
            clock = clock::get,
            onRevoke = { deviceId, householdId ->
                devices.revokeDevice(deviceId, householdId)
                auth.revoke(deviceId)
                registry.kick(deviceId)
            },
        )
        pairing = PairingService(db, clock = clock::get, tokens = tokens)
    }

    private fun config() = ServerConfig(
        version = "0.1.0",
        auth = auth,
        devices = devices,
        registry = registry,
        db = PostgresProbe(db),
        pairing = pairing,
        bootstrapToken = "boot-secret",
        tokenIssuer = tokens,
    )

    private data class Session(
        val deviceId: String,
        val householdId: String,
        val access: String,
        val refresh: String,
    )

    private fun seedWrap(household: String, device: String) {
        db.tenant(household) { connection ->
            connection.prepareStatement(
                "insert into household_key_wraps(household_id, device_id, wrapped_key) " +
                    "values (?::uuid, ?::uuid, ?)",
            ).use {
                it.setString(1, household)
                it.setString(2, device)
                it.setBytes(3, ByteArray(60) { index -> index.toByte() })
                it.executeUpdate()
            }
        }
    }

    private fun liveRefreshCount(household: String, device: String): Int =
        db.tenant(household) { connection ->
            connection.prepareStatement(
                "select count(*) from refresh_tokens " +
                    "where device_id = ?::uuid and revoked_at is null",
            ).use {
                it.setString(1, device)
                it.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
            }
        }

    private fun wrapCount(household: String, device: String): Int =
        db.tenant(household) { connection ->
            connection.prepareStatement(
                "select count(*) from household_key_wraps where device_id = ?::uuid",
            ).use {
                it.setString(1, device)
                it.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
            }
        }

    private fun revoked(household: String, device: String): Boolean =
        db.tenant(household) { connection ->
            connection.prepareStatement("select revoked_at from devices where id = ?::uuid").use {
                it.setString(1, device)
                it.executeQuery().use { rows -> rows.next() && rows.getTimestamp(1) != null }
            }
        }

    private fun hello(deviceId: String) = Envelope(
        v = 1,
        id = UUID.randomUUID().toString(),
        type = "device.hello",
        ts = 0,
        from = deviceId,
        body = buildJsonObject {
            put("platform", "android")
            put("name", "Pixel 9")
            put("appVersion", "0.1.0")
        },
    )

    private suspend fun HttpClient.mint(): String =
        post("/v1/pair/sessions") {
            header("Authorization", "Bearer boot-secret")
            header("X-Forwarded-For", "198.51.100.7")
            contentType(ContentType.Application.Json)
            setBody("""{"householdName":"Levi","memberName":"Amir"}""")
        }.bodyAsText().let { Json.parseToJsonElement(it).jsonObject["qr"]!!.jsonPrimitive.content }

    private suspend fun HttpClient.claim(session: String): Session {
        val response = post("/v1/pair/claim") {
            header("X-Forwarded-For", "198.51.100.7")
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("session", session)
                put("platform", "android")
                put("name", "Pixel 9")
                put("publicKey", Base64.getEncoder().encodeToString(ByteArray(32) { 2 }))
                put("alg", "x25519")
            }.toString())
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return Session(
            deviceId = body["deviceId"]!!.jsonPrimitive.content,
            householdId = body["householdId"]!!.jsonPrimitive.content,
            access = body["accessToken"]!!.jsonPrimitive.content,
            refresh = body["refreshToken"]!!.jsonPrimitive.content,
        )
    }

    private suspend fun claimOnce(client: HttpClient): Session {
        val qr = client.mint()
        return client.claim(qr.substringAfter("&p=").substringBefore("&"))
    }

    private suspend fun HttpClient.refresh(token: String): HttpResponse =
        post("/v1/token/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$token"}""")
        }

    @Test
    fun `refresh rotates both tokens and the new pair works`() = testApplication {
        application { module(config()) }
        val first = claimOnce(client)

        val rotated = client.refresh(first.refresh)

        assertEquals(HttpStatusCode.OK, rotated.status)
        val body = Json.parseToJsonElement(rotated.bodyAsText()).jsonObject
        val newRefresh = body["refreshToken"]!!.jsonPrimitive.content
        val newAccess = body["accessToken"]!!.jsonPrimitive.content
        assertNotEquals(first.refresh, newRefresh)
        assertNotEquals(first.access, newAccess)
        assertEquals(HttpStatusCode.OK, client.refresh(newRefresh).status)
    }

    @Test
    fun `reusing a rotated refresh token revokes the device the wrap and the live socket`() =
        testApplication {
            application { module(config()) }
            val first = claimOnce(client)
            seedWrap(first.householdId, first.deviceId)
            val rotated = Json.parseToJsonElement(client.refresh(first.refresh).bodyAsText()).jsonObject
            val newRefresh = rotated["refreshToken"]!!.jsonPrimitive.content
            val newAccess = rotated["accessToken"]!!.jsonPrimitive.content

            // A live socket on the rotated pair: the hard cut must close it,
            // not merely refuse the next connection.
            val http = createClient { install(WebSockets) }
            val socket = FakeDevice(first.deviceId, newAccess)
            socket.connect(http)
            socket.send(hello(first.deviceId))
            socket.expect("device.hello.ack")

            val reuse = client.refresh(first.refresh)

            assertEquals(HttpStatusCode.Unauthorized, reuse.status)
            assertEquals(HttpStatusCode.Unauthorized, client.refresh(newRefresh).status)
            socket.awaitClosed(5_000)
            assertTrue(revoked(first.householdId, first.deviceId))
            assertEquals(0, wrapCount(first.householdId, first.deviceId))
            assertEquals(0, liveRefreshCount(first.householdId, first.deviceId))

            val again = FakeDevice(first.deviceId, newAccess)
            again.connect(http)
            assertEquals("E_AUTH", again.expect("error").bodyString("code"))
        }

    @Test
    fun `a device revoked before rotation cannot mint a live token`() = testApplication {
        application { module(config()) }
        val first = claimOnce(client)
        seedWrap(first.householdId, first.deviceId)
        assertTrue(devices.revokeDevice(first.deviceId, first.householdId))
        // Simulate the narrow window: a refresh row that is still live while
        // the device is already revoked. The rotation must read revoked_at and
        // refuse; otherwise a revoked device could mint a fresh family.
        val lateRefresh = tokens.issueRefresh(first.deviceId, first.householdId).token

        val outcome = tokens.rotate(lateRefresh)

        assertTrue(outcome is RotateOutcome.Refused)
        assertEquals(0, liveRefreshCount(first.householdId, first.deviceId))
        assertEquals(0, wrapCount(first.householdId, first.deviceId))
        assertTrue(revoked(first.householdId, first.deviceId))
    }

    @Test
    fun `rotate and revoke racing leave no live refresh token`() = testApplication {
        application { module(config()) }
        val first = claimOnce(client)
        seedWrap(first.householdId, first.deviceId)
        val barrier = CyclicBarrier(2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val rotating = pool.submit<RotateOutcome> {
                barrier.await(5, TimeUnit.SECONDS)
                tokens.rotate(first.refresh)
            }
            val revoking = pool.submit<Boolean> {
                barrier.await(5, TimeUnit.SECONDS)
                devices.revokeDevice(first.deviceId, first.householdId)
            }
            rotating.get(10, TimeUnit.SECONDS)
            revoking.get(10, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }

        // Whichever side won, a revoked device may never keep a live family.
        assertEquals(0, liveRefreshCount(first.householdId, first.deviceId))
        assertEquals(0, wrapCount(first.householdId, first.deviceId))
        assertTrue(revoked(first.householdId, first.deviceId))
    }

    @Test
    fun `an expired access token is E_AUTH on the socket`() = testApplication {
        application { module(config()) }
        val first = claimOnce(client)
        auth.deviceFor(first.access)
        clock.addAndGet(16 * 60 * 1000)
        auth.revoke(first.deviceId)

        val http = createClient { install(WebSockets) }
        val socket = FakeDevice(first.deviceId, first.access)
        socket.connect(http)
        assertEquals("E_AUTH", socket.expect("error").bodyString("code"))
    }
}

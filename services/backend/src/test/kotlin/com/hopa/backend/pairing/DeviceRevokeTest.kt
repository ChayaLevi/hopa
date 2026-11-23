package com.hopa.backend.pairing

import com.hopa.backend.ServerConfig
import com.hopa.backend.gateway.DeviceRegistry
import com.hopa.backend.harness.FakeDevice
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.harness.bodyString
import com.hopa.backend.harness.newDevice
import com.hopa.backend.harness.newHousehold
import com.hopa.backend.harness.seedHousehold
import com.hopa.backend.module
import com.hopa.backend.protocol.Envelope
import com.hopa.backend.store.PgDevices
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Device inventory and revocation under R9/R10: one tenant transaction cuts the
 * token family and the wrap, then the live socket is kicked inside five seconds.
 */
class DeviceRevokeTest {
    private val db = PostgresHarness.appDatabase()
    private val clock = AtomicLong(System.currentTimeMillis())
    private lateinit var auth: PgAuth
    private lateinit var tokens: TokenIssuer
    private lateinit var registry: DeviceRegistry

    @BeforeEach
    fun setUp() {
        auth = PgAuth(db, clock = clock::get)
        tokens = TokenIssuer(db, clock = clock::get, onRevoke = { deviceId, _ -> auth.revoke(deviceId) })
        registry = DeviceRegistry()
    }

    private fun config() = ServerConfig(
        version = "0.1.0",
        auth = auth,
        devices = PgDevices(db),
        tokenIssuer = tokens,
        registry = registry,
    )

    private data class Paired(
        val household: String,
        val member: String,
        val phone: String,
        val phoneAccess: String,
        val pc: String,
        val pcAccess: String,
        val pcRefresh: String,
    )

    private val wrap = ByteArray(60) { index -> (index * 5 + 3).toByte() }

    /** A household with a phone and a PC, a wrap for the PC and tokens for both. */
    private fun seed(): Paired {
        val household = newHousehold()
        val member = UUID.randomUUID().toString()
        val phone = newDevice()
        val pc = newDevice()
        seedHousehold(db, household, phone, member)
        db.tenant(household) { connection ->
            connection.prepareStatement(
                "insert into devices(id, household_id, member_id, platform, name) " +
                    "values (?::uuid, ?::uuid, ?::uuid, 'windows', 'Studio')",
            ).use {
                it.setString(1, pc)
                it.setString(2, household)
                it.setString(3, member)
                it.executeUpdate()
            }
            connection.prepareStatement(
                "insert into household_key_wraps(household_id, device_id, wrapped_key) " +
                    "values (?::uuid, ?::uuid, ?)",
            ).use {
                it.setString(1, household)
                it.setString(2, pc)
                it.setBytes(3, wrap)
                it.executeUpdate()
            }
        }
        return Paired(
            household = household,
            member = member,
            phone = phone,
            phoneAccess = tokens.issueAccess(phone, household, member).token,
            pc = pc,
            pcAccess = tokens.issueAccess(pc, household, member).token,
            pcRefresh = tokens.issueRefresh(pc, household).token,
        )
    }

    private data class OtherDevice(val household: String, val device: String, val access: String)

    private fun seedOther(): OtherDevice {
        val household = newHousehold()
        val member = UUID.randomUUID().toString()
        val device = newDevice()
        seedHousehold(db, household, device, member)
        return OtherDevice(household, device, tokens.issueAccess(device, household, member).token)
    }

    private fun hello(deviceId: String) = Envelope(
        v = 1,
        id = UUID.randomUUID().toString(),
        type = "device.hello",
        ts = 0,
        from = deviceId,
        body = buildJsonObject {
            put("platform", "windows")
            put("name", "Studio")
            put("appVersion", "0.1.0")
        },
    )

    private suspend fun HttpClient.devices(bearer: String): HttpResponse =
        get("/v1/devices") { header(HttpHeaders.Authorization, "Bearer $bearer") }

    private suspend fun HttpClient.revoke(bearer: String, id: String): HttpResponse =
        delete("/v1/devices/$id") { header(HttpHeaders.Authorization, "Bearer $bearer") }

    private suspend fun HttpClient.refresh(token: String): HttpResponse =
        post("/v1/token/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$token"}""")
        }

    private suspend fun bodyOf(response: HttpResponse): JsonObject =
        Json.parseToJsonElement(response.bodyAsText()).jsonObject

    private suspend fun devicesOf(response: HttpResponse): List<JsonObject> =
        Json.parseToJsonElement(response.bodyAsText()).jsonArray.map { it.jsonObject }

    private suspend fun errorCode(response: HttpResponse): String =
        bodyOf(response)["code"]!!.jsonPrimitive.content

    private fun JsonObject.id(): String = this["id"]!!.jsonPrimitive.content

    private fun JsonObject.current(): Boolean = this["current"]!!.jsonPrimitive.boolean

    private fun JsonObject.revokedAt(): String? =
        this["revokedAt"]?.jsonPrimitive?.content

    private fun count(household: String, sql: String, bind: String): Int =
        db.tenant(household) { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, bind)
                statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
            }
        }

    private fun wrapCount(household: String, device: String): Int =
        count(household, "select count(*) from household_key_wraps where device_id = ?::uuid", device)

    private fun accessCount(household: String, device: String): Int =
        count(household, "select count(*) from access_tokens where device_id = ?::uuid", device)

    private fun liveRefreshCount(household: String, device: String): Int =
        count(
            household,
            "select count(*) from refresh_tokens where device_id = ?::uuid and revoked_at is null",
            device,
        )

    private fun revokedAt(household: String, device: String): String? =
        db.tenant(household) { connection ->
            connection.prepareStatement("select revoked_at from devices where id = ?::uuid").use {
                it.setString(1, device)
                it.executeQuery().use { rows ->
                    if (!rows.next()) null else rows.getTimestamp(1)?.toInstant()?.toString()
                }
            }
        }

    /** The handler's finally runs just after the client sees the close frame. */
    private suspend fun awaitOffline(deviceId: String, timeoutMs: Long = 5_000) {
        try {
            withTimeout(timeoutMs) {
                while (registry.online(deviceId) || registry.connected(deviceId)) {
                    delay(10)
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw AssertionError(
                "$deviceId: registry still reports the device connected/online",
                timeout,
            )
        }
    }

    @Test
    fun `the device list is household scoped and marks the caller`() = testApplication {
        application { module(config()) }
        val paired = seed()
        val stranger = seedOther()

        val response = client.devices(paired.phoneAccess)

        assertEquals(HttpStatusCode.OK, response.status)
        val listed = devicesOf(response)
        assertEquals(2, listed.size)
        assertEquals(setOf(paired.phone, paired.pc), listed.map { it.id() }.toSet())
        assertFalse(listed.any { it.id() == stranger.device })
        assertTrue(listed.single { it.id() == paired.phone }.current())
        assertFalse(listed.single { it.id() == paired.pc }.current())
        listed.forEach { assertNull(it.revokedAt()) }

        // The order is deterministic across calls.
        assertEquals(listed.map { it.id() }, devicesOf(client.devices(paired.phoneAccess)).map { it.id() })
    }

    @Test
    fun `revoking a connected device closes the socket within five seconds and cuts every credential`() =
        testApplication {
            application { module(config()) }
            val paired = seed()
            val http = createClient { install(WebSockets) }
            val socket = FakeDevice(paired.pc, paired.pcAccess)
            socket.connect(http)
            socket.send(hello(paired.pc))
            socket.expect("device.hello.ack")

            val revoked = client.revoke(paired.phoneAccess, paired.pc)

            assertEquals(HttpStatusCode.NoContent, revoked.status)
            socket.awaitClosed(5_000)
            awaitOffline(paired.pc)
            assertFalse(registry.online(paired.pc))
            assertFalse(registry.connected(paired.pc))

            // The deleted access token cannot open a new gateway socket.
            val again = FakeDevice(paired.pc, paired.pcAccess)
            again.connect(http)
            assertEquals("E_AUTH", again.expect("error").bodyString("code"))

            // The refresh family is dead.
            val refresh = client.refresh(paired.pcRefresh)
            assertEquals(HttpStatusCode.Unauthorized, refresh.status)
            assertEquals("E_AUTH", errorCode(refresh))

            assertEquals(0, wrapCount(paired.household, paired.pc))
            assertEquals(0, accessCount(paired.household, paired.pc))
            assertEquals(0, liveRefreshCount(paired.household, paired.pc))

            // The row remains, now marked revoked, so the phone can still show it.
            val listed = devicesOf(client.devices(paired.phoneAccess))
            assertEquals(2, listed.size)
            val pc = listed.single { it.id() == paired.pc }
            assertFalse(pc.current())
            assertTrue(pc.revokedAt() != null, "the revoked device must stay listed with revokedAt")

            // A repeat DELETE is idempotent and must not move the timestamp.
            val revokedFirst = revokedAt(paired.household, paired.pc)
            val againDeleted = client.revoke(paired.phoneAccess, paired.pc)
            assertEquals(HttpStatusCode.NoContent, againDeleted.status)
            assertEquals(revokedFirst, revokedAt(paired.household, paired.pc))
            assertEquals(0, wrapCount(paired.household, paired.pc))
            assertEquals(0, accessCount(paired.household, paired.pc))
        }

    @Test
    fun `revoking another household's device is E_TENANT and changes nothing`() = testApplication {
        application { module(config()) }
        val paired = seed()
        val stranger = seedOther()

        val response = client.revoke(paired.phoneAccess, stranger.device)

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("E_TENANT", errorCode(response))
        assertNull(revokedAt(stranger.household, stranger.device))
        assertEquals(1, accessCount(stranger.household, stranger.device))
        assertEquals(1, wrapCount(paired.household, paired.pc))
        assertEquals(1, accessCount(paired.household, paired.pc))
    }

    @Test
    fun `a refresh family already rotated once is refused after revoke`() = testApplication {
        application { module(config()) }
        val paired = seed()
        val rotated = tokens.rotate(paired.pcRefresh) as RotateOutcome.Rotated

        assertEquals(HttpStatusCode.NoContent, client.revoke(paired.phoneAccess, paired.pc).status)

        // The surviving token of the rotated family is refused after revoke.
        val currentRefresh = client.refresh(rotated.refresh.token)
        assertEquals(HttpStatusCode.Unauthorized, currentRefresh.status)
        assertEquals("E_AUTH", errorCode(currentRefresh))

        // The pre-rotated token is refused too (the theft path must stay closed).
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.refresh(paired.pcRefresh).status,
        )

        val http = createClient { install(WebSockets) }
        val socket = FakeDevice(paired.pc, rotated.access.token)
        socket.connect(http)
        assertEquals("E_AUTH", socket.expect("error").bodyString("code"))
    }

    @Test
    fun `a malformed device id is E_SCHEMA and a missing bearer is E_AUTH`() = testApplication {
        application { module(config()) }
        val paired = seed()

        val malformed = client.revoke(paired.phoneAccess, "not-a-uuid")
        val anonymous = client.get("/v1/devices")

        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        assertEquals("E_SCHEMA", errorCode(malformed))
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
        assertEquals("E_AUTH", errorCode(anonymous))
        assertNull(revokedAt(paired.household, paired.pc))
    }
}

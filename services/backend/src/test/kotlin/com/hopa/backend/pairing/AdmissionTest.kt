package com.hopa.backend.pairing

import com.hopa.backend.ServerConfig
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.harness.newDevice
import com.hopa.backend.harness.newHousehold
import com.hopa.backend.harness.seedHousehold
import com.hopa.backend.module
import io.ktor.client.HttpClient
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
import java.sql.Connection
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The admission journey: a phone that already belongs to household A mints a
 * pending session as the PC, vouches for it with the household key wrapped to
 * the PC's X25519 key, and the PC claims it with the same key. The wrap parks on
 * the pending row until that claim, then lands in `household_key_wraps` together
 * with the device and its tokens.
 */
class AdmissionTest {
    private val db = PostgresHarness.appDatabase()
    private val clock = AtomicLong(System.currentTimeMillis())

    // The server treats the wrap as opaque bytes: R8a's 12-byte IV, 32-byte
    // ciphertext and 16-byte tag, never unwrapped here.
    private val wrap = ByteArray(60) { index -> (index * 3 + 1).toByte() }
    private val otherWrap = ByteArray(60) { index -> (index + 7).toByte() }
    private val pcKey = ByteArray(32) { index -> (index + 1).toByte() }
    private val otherKey = ByteArray(32) { index -> (index + 101).toByte() }
    private val phoneKey = ByteArray(32) { index -> (index * 5 + 3).toByte() }

    private data class Phone(
        val household: String,
        val member: String,
        val device: String,
        val token: String,
    )

    private fun config() = ServerConfig(
        version = "0.1.0",
        auth = PgAuth(db, clock = clock::get),
        bootstrapToken = BOOTSTRAP,
        pairing = PairingService(db, clock = clock::get),
        pendingPairings = PendingPairingService(db, clock = clock::get),
    )

    private fun seedPhone(): Phone {
        val household = newHousehold()
        val member = UUID.randomUUID().toString()
        val device = newDevice()
        seedHousehold(db, household, device, member)
        db.tenant(household) { connection ->
            connection.prepareStatement(
                "update devices set public_key = ?, alg = 'x25519' where id = ?::uuid",
            ).use { statement ->
                statement.setBytes(1, phoneKey)
                statement.setString(2, device)
                statement.executeUpdate()
            }
        }
        val token = TokenIssuer(db, clock::get).issueAccess(device, household, member).token
        return Phone(household, member, device, token)
    }

    private suspend fun HttpClient.mintPending(
        publicKey: ByteArray = pcKey,
        platform: String = "windows",
        name: String = "Studio",
        address: String = "198.51.100.20",
    ): HttpResponse = post("/v1/pair/pending") {
        header("X-Forwarded-For", address)
        contentType(ContentType.Application.Json)
        setBody(
            buildJsonObject {
                put("platform", platform)
                put("name", name)
                put("publicKey", Base64.getEncoder().encodeToString(publicKey))
                put("alg", "x25519")
            }.toString(),
        )
    }

    private suspend fun HttpClient.resolve(
        secret: String,
        address: String = "198.51.100.22",
    ): HttpResponse = post("/v1/pair/pending/resolve") {
        header("X-Forwarded-For", address)
        contentType(ContentType.Application.Json)
        setBody(buildJsonObject { put("sessionSecret", secret) }.toString())
    }

    private suspend fun HttpClient.vouch(
        secret: String,
        wrapBytes: ByteArray = wrap,
        bearer: String? = null,
        address: String = "198.51.100.21",
    ): HttpResponse = post("/v1/pair/vouch") {
        if (bearer != null) header(HttpHeaders.Authorization, "Bearer $bearer")
        header("X-Forwarded-For", address)
        contentType(ContentType.Application.Json)
        setBody(
            buildJsonObject {
                put("sessionSecret", secret)
                put("wrappedKey", base64Url(wrapBytes))
            }.toString(),
        )
    }

    private suspend fun HttpClient.claim(
        session: String,
        publicKey: ByteArray = pcKey,
        platform: String = "windows",
        name: String = "Studio",
        address: String = "198.51.100.23",
    ): HttpResponse = post("/v1/pair/claim") {
        header("X-Forwarded-For", address)
        contentType(ContentType.Application.Json)
        setBody(
            buildJsonObject {
                put("session", session)
                put("platform", platform)
                put("name", name)
                put("publicKey", Base64.getEncoder().encodeToString(publicKey))
                put("alg", "x25519")
            }.toString(),
        )
    }

    private suspend fun HttpClient.operatorMint(
        address: String = "198.51.100.24",
    ): HttpResponse = post("/v1/pair/sessions") {
        header(HttpHeaders.Authorization, "Bearer $BOOTSTRAP")
        header("X-Forwarded-For", address)
        contentType(ContentType.Application.Json)
        setBody("""{"householdName":"Levi","memberName":"PC"}""")
    }

    private fun bodyOf(response: String): JsonObject = Json.parseToJsonElement(response).jsonObject

    private suspend fun bodyOf(response: HttpResponse): JsonObject =
        bodyOf(response.bodyAsText())

    private suspend fun errorCode(response: HttpResponse): String =
        bodyOf(response)["code"]!!.jsonPrimitive.content

    private suspend fun errorMessage(response: HttpResponse): String =
        bodyOf(response)["message"]!!.jsonPrimitive.content

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun hashHex(secret: String): String = Tokens.hex(Tokens.hash(secret))

    private fun count(household: String, sql: String): Int =
        db.tenant(household) { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows -> rows.next(); rows.getInt(1) }
            }
        }

    private data class PendingRow(
        val householdId: String?,
        val memberId: String?,
        val wrap: ByteArray?,
        val voucherDeviceId: String?,
        val vouched: Boolean,
        val claimed: Boolean,
    )

    private fun pendingRow(secret: String): PendingRow =
        db.token(hashHex(secret)) { connection ->
            connection.prepareStatement(
                "select household_id::text, member_id::text, wrapped_key, " +
                    "voucher_device_id::text, vouched_at, claimed_at from pending_pairings " +
                    "where code_hash = decode(?, 'hex')",
            ).use { statement ->
                statement.setString(1, hashHex(secret))
                statement.executeQuery().use { rows ->
                    check(rows.next()) { "the pending row must be readable by its secret" }
                    PendingRow(
                        householdId = rows.getString(1),
                        memberId = rows.getString(2),
                        wrap = rows.getBytes(3),
                        voucherDeviceId = rows.getString(4),
                        vouched = rows.getTimestamp(5) != null,
                        claimed = rows.getTimestamp(6) != null,
                    )
                }
            }
        }

    private data class DeviceRow(val id: String, val publicKey: ByteArray?)

    private fun pcDevices(household: String): List<DeviceRow> =
        db.tenant(household) { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "select id::text, public_key from devices " +
                        "where platform = 'windows' and name = 'Studio'",
                ).use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(DeviceRow(rows.getString(1), rows.getBytes(2)))
                        }
                    }
                }
            }
        }

    private fun storedWraps(household: String): List<ByteArray> =
        db.tenant(household) { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select wrapped_key from household_key_wraps").use { rows ->
                    buildList {
                        while (rows.next()) add(rows.getBytes(1))
                    }
                }
            }
        }

    private fun Connection.countRows(sql: String): Int =
        createStatement().use { statement ->
            statement.executeQuery(sql).use { rows -> rows.next(); rows.getInt(1) }
        }

    @Test
    fun `a phone vouches a pending session and the pc claims the parked wrap`() = testApplication {
        application { module(config()) }
        val phone = seedPhone()
        val minted = bodyOf(client.mintPending())
        val secret = minted["secret"]!!.jsonPrimitive.content
        val sessionId = minted["sessionId"]!!.jsonPrimitive.content

        assertEquals("pending", bodyOf(client.resolve(secret))["state"]!!.jsonPrimitive.content)

        val vouched = client.vouch(secret, bearer = phone.token)

        assertEquals(HttpStatusCode.OK, vouched.status)
        val acknowledged = bodyOf(vouched)
        assertEquals(sessionId, acknowledged["sessionId"]!!.jsonPrimitive.content)
        assertEquals(phone.household, acknowledged["householdId"]!!.jsonPrimitive.content)
        assertEquals(phone.member, acknowledged["memberId"]!!.jsonPrimitive.content)
        assertEquals("vouched", bodyOf(client.resolve(secret))["state"]!!.jsonPrimitive.content)

        val parked = pendingRow(secret)
        assertEquals(phone.household, parked.householdId)
        assertEquals(phone.member, parked.memberId)
        assertArrayEquals(wrap, parked.wrap)
        assertEquals(phone.device, parked.voucherDeviceId)
        assertTrue(parked.vouched)
        assertFalse(parked.claimed)
        // R25: no device exists yet, so no wrap row may exist yet either.
        assertEquals(0, storedWraps(phone.household).size)

        val claimed = client.claim(secret)

        assertEquals(HttpStatusCode.Created, claimed.status)
        val body = bodyOf(claimed)
        val deviceId = body["deviceId"]!!.jsonPrimitive.content
        assertEquals(phone.household, body["householdId"]!!.jsonPrimitive.content)
        assertArrayEquals(
            wrap,
            Base64.getUrlDecoder().decode(body["wrappedKey"]!!.jsonPrimitive.content),
        )
        assertArrayEquals(
            phoneKey,
            Base64.getUrlDecoder().decode(body["voucherPublicKey"]!!.jsonPrimitive.content),
        )
        assertTrue(body["accessToken"]!!.jsonPrimitive.content.length >= 43)

        val devices = pcDevices(phone.household)
        assertEquals(1, devices.size)
        assertEquals(deviceId, devices.single().id)
        assertArrayEquals(pcKey, devices.single().publicKey)
        val wraps = storedWraps(phone.household)
        assertEquals(1, wraps.size)
        assertArrayEquals(wrap, wraps.single())

        // The claim issued real tokens for the new device.
        val accessHash = Tokens.hex(Tokens.hash(body["accessToken"]!!.jsonPrimitive.content))
        assertEquals(
            1,
            db.token(accessHash) { it.countRows("select count(*) from access_tokens") },
        )
        assertEquals(
            1,
            db.token(accessHash) { connection ->
                connection.prepareStatement(
                    "select count(*) from access_tokens where device_id = ?::uuid",
                ).use { statement ->
                    statement.setString(1, deviceId)
                    statement.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
                }
            },
        )
        assertEquals("claimed", bodyOf(client.resolve(secret))["state"]!!.jsonPrimitive.content)
        assertTrue(pendingRow(secret).claimed)
    }

    @Test
    fun `a vouch without a device bearer is E_AUTH`() = testApplication {
        application { module(config()) }
        val phone = seedPhone()
        val secret = bodyOf(client.mintPending())["secret"]!!.jsonPrimitive.content

        val missing = client.vouch(secret)
        val unknown = client.vouch(secret, bearer = "not-a-token")

        assertEquals(HttpStatusCode.Unauthorized, missing.status)
        assertEquals("E_AUTH", errorCode(missing))
        assertEquals(HttpStatusCode.Unauthorized, unknown.status)
        assertEquals("E_AUTH", errorCode(unknown))
        assertFalse(pendingRow(secret).vouched)
    }

    @Test
    fun `an unknown secret or a second vouch is E_SCHEMA without changing the wrap`() = testApplication {
        application { module(config()) }
        val phone = seedPhone()
        val secret = bodyOf(client.mintPending())["secret"]!!.jsonPrimitive.content

        val unknown = client.vouch(Tokens.mint(), bearer = phone.token)
        assertEquals(HttpStatusCode.BadRequest, unknown.status)
        assertEquals("E_SCHEMA", errorCode(unknown))
        assertEquals("unknown, expired or already vouched pairing session", errorMessage(unknown))

        assertEquals(
            HttpStatusCode.OK,
            client.vouch(secret, wrap, bearer = phone.token).status,
        )
        val again = client.vouch(secret, otherWrap, bearer = phone.token)

        assertEquals(HttpStatusCode.BadRequest, again.status)
        assertEquals("E_SCHEMA", errorCode(again))
        assertEquals("unknown, expired or already vouched pairing session", errorMessage(again))
        assertArrayEquals(wrap, pendingRow(secret).wrap)
        assertEquals(0, storedWraps(phone.household).size)
    }

    @Test
    fun `a wrap that is not exactly 60 bytes is E_SCHEMA`() = testApplication {
        application { module(config()) }
        val phone = seedPhone()
        val secret = bodyOf(client.mintPending())["secret"]!!.jsonPrimitive.content

        val short = client.vouch(secret, ByteArray(59) { 1 }, bearer = phone.token)
        val long = client.vouch(secret, ByteArray(61) { 1 }, bearer = phone.token, address = "198.51.100.25")
        val empty = client.vouch(secret, ByteArray(0), bearer = phone.token, address = "198.51.100.26")

        for (response in listOf(short, long, empty)) {
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals("E_SCHEMA", errorCode(response))
        }
        assertFalse(pendingRow(secret).vouched)
    }

    @Test
    fun `the eleventh vouch from one device in a minute is E_RATE`() = testApplication {
        application { module(config()) }
        val phone = seedPhone()
        val secret = bodyOf(client.mintPending())["secret"]!!.jsonPrimitive.content

        repeat(10) {
            client.vouch(secret, wrap, bearer = phone.token)
        }
        val denied = client.vouch(secret, wrap, bearer = phone.token)

        assertEquals(HttpStatusCode.TooManyRequests, denied.status)
        assertEquals("E_RATE", errorCode(denied))
    }

    @Test
    fun `a claim before the vouch is E_SCHEMA and leaves the session pending`() = testApplication {
        application { module(config()) }
        seedPhone()
        val secret = bodyOf(client.mintPending())["secret"]!!.jsonPrimitive.content

        val response = client.claim(secret)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("E_SCHEMA", errorCode(response))
        assertEquals("pairing session has not been vouched", errorMessage(response))
        assertEquals("pending", bodyOf(client.resolve(secret))["state"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a claimed pending session cannot be claimed twice`() = testApplication {
        application { module(config()) }
        val phone = seedPhone()
        val secret = bodyOf(client.mintPending())["secret"]!!.jsonPrimitive.content
        assertEquals(HttpStatusCode.OK, client.vouch(secret, bearer = phone.token).status)
        assertEquals(HttpStatusCode.Created, client.claim(secret).status)

        val again = client.claim(secret)

        assertEquals(HttpStatusCode.BadRequest, again.status)
        assertEquals("E_SCHEMA", errorCode(again))
        assertEquals(1, pcDevices(phone.household).size)
        assertEquals(1, storedWraps(phone.household).size)
    }

    @Test
    fun `an expired pending session cannot be claimed`() = testApplication {
        application { module(config()) }
        val phone = seedPhone()
        val secret = bodyOf(client.mintPending())["secret"]!!.jsonPrimitive.content
        assertEquals(HttpStatusCode.OK, client.vouch(secret, bearer = phone.token).status)

        clock.addAndGet(300_001)
        val response = client.claim(secret)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("E_SCHEMA", errorCode(response))
        assertEquals("expired", bodyOf(client.resolve(secret))["state"]!!.jsonPrimitive.content)
        assertEquals(0, pcDevices(phone.household).size)
        assertEquals(0, storedWraps(phone.household).size)
    }

    @Test
    fun `vouching one session does not admit another session`() = testApplication {
        application { module(config()) }
        val phone = seedPhone()
        val vouched = bodyOf(client.mintPending())
        val other = bodyOf(client.mintPending(address = "198.51.100.27"))
        val vouchedSecret = vouched["secret"]!!.jsonPrimitive.content
        val otherSecret = other["secret"]!!.jsonPrimitive.content
        assertNotEquals(vouchedSecret, otherSecret)
        assertEquals(HttpStatusCode.OK, client.vouch(vouchedSecret, bearer = phone.token).status)

        val swapped = client.claim(otherSecret, address = "198.51.100.28")

        assertEquals(HttpStatusCode.BadRequest, swapped.status)
        assertEquals("E_SCHEMA", errorCode(swapped))
        assertEquals(0, storedWraps(phone.household).size)
        assertEquals(
            "pending",
            bodyOf(client.resolve(otherSecret))["state"]!!.jsonPrimitive.content,
        )
        // The session that was actually vouched still admits the PC.
        assertEquals(HttpStatusCode.Created, client.claim(vouchedSecret).status)
    }

    @Test
    fun `a claim must present the public key that was vouched for`() = testApplication {
        application { module(config()) }
        val phone = seedPhone()
        val secret = bodyOf(client.mintPending(pcKey))["secret"]!!.jsonPrimitive.content
        assertEquals(HttpStatusCode.OK, client.vouch(secret, bearer = phone.token).status)

        val wrongKey = client.claim(secret, otherKey)

        assertEquals(HttpStatusCode.BadRequest, wrongKey.status)
        assertEquals("E_SCHEMA", errorCode(wrongKey))
        assertEquals(0, pcDevices(phone.household).size)
        assertEquals(0, storedWraps(phone.household).size)
        assertEquals("vouched", bodyOf(client.resolve(secret))["state"]!!.jsonPrimitive.content)

        val rightKey = client.claim(secret, pcKey, address = "198.51.100.29")

        assertEquals(HttpStatusCode.Created, rightKey.status)
        assertEquals(1, pcDevices(phone.household).size)
        assertEquals(1, storedWraps(phone.household).size)
        assertArrayEquals(pcKey, pcDevices(phone.household).single().publicKey)
    }

    @Test
    fun `a claim without the voucher public key is E_SCHEMA and admits nothing`() = testApplication {
        application { module(config()) }
        val phone = seedPhone()
        val secret = bodyOf(client.mintPending())["secret"]!!.jsonPrimitive.content
        assertEquals(HttpStatusCode.OK, client.vouch(secret, bearer = phone.token).status)

        // A vouched session whose phone device lost its key cannot be unwrapped.
        db.tenant(phone.household) { connection ->
            connection.prepareStatement(
                "update devices set public_key = null where id = ?::uuid",
            ).use { statement ->
                statement.setString(1, phone.device)
                statement.executeUpdate()
            }
        }

        val response = client.claim(secret)

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("E_SCHEMA", errorCode(response))
        assertEquals("pairing session has no voucher public key", errorMessage(response))
        assertEquals(0, pcDevices(phone.household).size)
        assertEquals(0, storedWraps(phone.household).size)
        assertEquals("vouched", bodyOf(client.resolve(secret))["state"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an operator minted session still claims without a wrapped key`() = testApplication {
        application { module(config()) }
        val qr = bodyOf(client.operatorMint())["qr"]!!.jsonPrimitive.content
        val session = qr.substringAfter("&p=").substringBefore("&")

        val response = client.claim(session, address = "198.51.100.31")

        assertEquals(HttpStatusCode.Created, response.status)
        val body = bodyOf(response)
        assertFalse(body.containsKey("wrappedKey"))
        assertFalse(body.containsKey("voucherPublicKey"))
        val household = body["householdId"]!!.jsonPrimitive.content
        assertEquals(0, storedWraps(household).size)
        assertEquals(1, count(household, "select count(*) from devices"))
    }
}

private const val BOOTSTRAP = "boot-secret"

package com.hopa.backend.admin

import com.hopa.backend.ServerConfig
import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.harness.FakeDevice
import com.hopa.backend.harness.FakeFcm
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.harness.newDevice
import com.hopa.backend.harness.newHousehold
import com.hopa.backend.harness.seedHousehold
import com.hopa.backend.log.EventLog
import com.hopa.backend.module
import com.hopa.backend.pairing.PgAuth
import com.hopa.backend.pairing.Tokens
import com.hopa.backend.protocol.Envelope
import com.hopa.backend.protocol.EnvelopeValidator
import com.hopa.backend.protocol.GatewayCodec
import com.hopa.backend.push.PushOutcome
import com.hopa.backend.push.PushPort
import com.hopa.backend.store.PgDevices
import com.hopa.backend.store.PgTaskStore
import com.hopa.backend.store.PostgresProbe
import com.hopa.backend.tasks.DeviceDirectory
import com.hopa.backend.trace.PgTraceStore
import com.hopa.backend.trace.TraceIds
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The admin console's device inventory and its diagnostic sender. An admin
 * token is minted straight into `admin_sessions` (slice B owns the session
 * route); the rest is the real HTTP surface, the real gateway and the real
 * Postgres task store.
 */
class DiagSendTest {
    private val db = PostgresHarness.appDatabase()
    private val events = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()

    @BeforeEach
    fun setUp() {
        events.clear()
    }

    private data class Admin(val id: String, val token: String)

    /** Mints an admin session row directly: `POST /v1/admin/session` is slice B's. */
    private fun seedAdmin(expiresAt: Long = System.currentTimeMillis() + 3_600_000): Admin {
        val adminId = UUID.randomUUID().toString()
        val token = Tokens.mint()
        db.read { connection ->
            connection.prepareStatement(
                "insert into admins(id, email, password_hash, role) " +
                    "values (?::uuid, ?, 'not-used', 'admin')",
            ).use {
                it.setString(1, adminId)
                it.setString(2, "admin-${UUID.randomUUID()}@hopa.test")
                it.executeUpdate()
            }
            connection.prepareStatement(
                "insert into admin_sessions(id, admin_id, token_hash, expires_at) " +
                    "values (gen_random_uuid(), ?::uuid, ?, ?)",
            ).use {
                it.setString(1, adminId)
                it.setBytes(2, Tokens.hash(token))
                it.setTimestamp(3, Timestamp.from(Instant.ofEpochMilli(expiresAt)))
                it.executeUpdate()
            }
        }
        return Admin(adminId, token)
    }

    private fun accessToken(household: String, device: String): String {
        val token = Tokens.mint()
        db.tenant(household) { connection ->
            connection.prepareStatement(
                "insert into access_tokens(id, device_id, household_id, token_hash, expires_at) " +
                    "values (gen_random_uuid(), ?::uuid, ?::uuid, ?, ?)",
            ).use {
                it.setString(1, device)
                it.setString(2, household)
                it.setBytes(3, Tokens.hash(token))
                it.setTimestamp(
                    4,
                    Timestamp.from(Instant.ofEpochMilli(System.currentTimeMillis() + 60_000)),
                )
                it.executeUpdate()
            }
        }
        return token
    }

    private fun extraDevice(household: String, member: String): String {
        val device = newDevice()
        db.tenant(household) { connection ->
            connection.prepareStatement(
                "insert into devices(id, household_id, member_id, platform, name) " +
                    "values (?::uuid, ?::uuid, ?::uuid, 'windows', 'Desk')",
            ).use {
                it.setString(1, device)
                it.setString(2, household)
                it.setString(3, member)
                it.executeUpdate()
            }
        }
        return device
    }

    private fun memberOf(household: String, device: String): String =
        db.tenant(household) { connection ->
            connection.prepareStatement("select member_id from devices where id = ?::uuid").use {
                it.setString(1, device)
                it.executeQuery().use { rows -> rows.next(); rows.getString(1) }
            }
        }

    private fun setPushToken(household: String, device: String, token: String) {
        db.tenant(household) { connection ->
            connection.prepareStatement(
                "update devices set push_token = ? where id = ?::uuid",
            ).use {
                it.setString(1, token)
                it.setString(2, device)
                it.executeUpdate()
            }
        }
    }

    private fun revoke(household: String, device: String) {
        db.tenant(household) { connection ->
            connection.prepareStatement(
                "update devices set revoked_at = now() where id = ?::uuid",
            ).use {
                it.setString(1, device)
                it.executeUpdate()
            }
        }
    }

    private val traceStore = PgTraceStore(db)

    private fun config(push: PushPort = PushPort.None) =
        ServerConfig(
            version = "0.1.0",
            auth = PgAuth(db),
            devices = PgDevices(db),
            db = PostgresProbe(db),
            tasks = PgTaskStore(db),
            push = push,
            log = EventLog { name, fields -> events += name to fields },
            traces = traceStore,
            traceQuery = traceStore,
            adminAuth = AdminAuth(db),
        )

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

    private suspend fun ApplicationTestBuilder.connect(device: String, token: String): FakeDevice {
        val http = createClient { install(WebSockets) }
        val fake = FakeDevice(device, token)
        fake.connect(http)
        fake.send(hello(device))
        fake.expect("device.hello.ack")
        return fake
    }

    private suspend fun HttpClient.devices(
        token: String?,
        household: String?,
    ): HttpResponse = get("/v1/admin/devices" + (household?.let { "?household=$it" } ?: "")) {
        if (token != null) header("Authorization", "Bearer $token")
    }

    private suspend fun HttpClient.diag(
        token: String?,
        device: String,
        household: String,
        type: String,
        body: JsonObject? = null,
    ): HttpResponse = post("/v1/admin/devices/$device/diag") {
        if (token != null) header("Authorization", "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(
            buildJsonObject {
                put("household", household)
                put("type", type)
                if (body != null) put("body", body)
            }.toString(),
        )
    }

    private fun code(response: String): String? =
        Json.parseToJsonElement(response).jsonObject["code"]?.jsonPrimitive?.content

    private fun taskCount(household: String): Int =
        db.tenant(household) { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from tasks").use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }

    private fun taskState(household: String, taskId: String): String? =
        db.tenant(household) { connection ->
            connection.prepareStatement("select state from tasks where id = ?::uuid").use {
                it.setString(1, taskId)
                it.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            }
        }

    private fun taskType(household: String, taskId: String): String? =
        db.tenant(household) { connection ->
            connection.prepareStatement("select type from tasks where id = ?::uuid").use {
                it.setString(1, taskId)
                it.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            }
        }

    private fun awaitTaskState(household: String, taskId: String, expected: String): String? {
        val deadline = System.currentTimeMillis() + 2_000
        var state = taskState(household, taskId)
        while (state != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            state = taskState(household, taskId)
        }
        return state
    }

    private fun diagEvents() = events.filter { it.first == "diag.send" }

    @Test
    fun `admin routes refuse missing, unknown, expired and device tokens`() = testApplication {
        val admin = seedAdmin()
        val expired = seedAdmin(expiresAt = System.currentTimeMillis() - 60_000)
        val household = newHousehold()
        val device = newDevice()
        seedHousehold(db, household, device, UUID.randomUUID().toString())
        val deviceToken = accessToken(household, device)
        application { module(config()) }

        var response = client.devices(token = null, household = household)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("E_AUTH", code(response.bodyAsText()))

        response = client.devices(token = "not-a-token", household = household)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("E_AUTH", code(response.bodyAsText()))

        response = client.devices(token = expired.token, household = household)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("E_AUTH", code(response.bodyAsText()))

        response = client.devices(token = deviceToken, household = household)
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("E_AUTH", code(response.bodyAsText()))

        response = client.diag(
            token = deviceToken,
            device = device,
            household = household,
            type = "diag.ping",
        )
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("E_AUTH", code(response.bodyAsText()))
        assertEquals(0, taskCount(household))
        assertNotNull(admin.token)
    }

    @Test
    fun `the device list reports presence and never another household`() = testApplication {
        val admin = seedAdmin()
        val household = newHousehold()
        val phone = newDevice()
        seedHousehold(db, household, phone, UUID.randomUUID().toString())
        val desk = extraDevice(household, memberOf(household, phone))
        val otherHousehold = newHousehold()
        val other = newDevice()
        seedHousehold(db, otherHousehold, other, UUID.randomUUID().toString())
        application { module(config()) }
        val phoneWs = connect(phone, accessToken(household, phone))

        val response = client.devices(admin.token, household)
        assertEquals(HttpStatusCode.OK, response.status)
        val rows = Json.parseToJsonElement(response.bodyAsText()).jsonArray
        assertEquals(2, rows.size)
        val online = rows.first { it.jsonObject["id"]!!.jsonPrimitive.content == phone }
        val offline = rows.first { it.jsonObject["id"]!!.jsonPrimitive.content == desk }
        assertEquals(true, online.jsonObject["online"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(false, offline.jsonObject["online"]!!.jsonPrimitive.content.toBoolean())
        assertFalse(rows.any { it.jsonObject["id"]!!.jsonPrimitive.content == other })
        phoneWs.close()
    }

    @Test
    fun `a malformed household is E_SCHEMA`() = testApplication {
        val admin = seedAdmin()
        application { module(config()) }

        var response = client.devices(admin.token, household = "not-a-uuid")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("E_SCHEMA", code(response.bodyAsText()))

        response = client.devices(admin.token, household = null)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("E_SCHEMA", code(response.bodyAsText()))
    }

    @Test
    fun `an online diag ping is delivered plaintext and the device result closes it`() =
        testApplication {
            val fcm = FakeFcm()
            try {
                val admin = seedAdmin()
                val household = newHousehold()
                val device = newDevice()
                seedHousehold(db, household, device, UUID.randomUUID().toString())
                application { module(config(fcm.push())) }
                val deviceWs = connect(device, accessToken(household, device))

                val response = client.diag(
                    admin.token,
                    device,
                    household,
                    "diag.ping",
                    buildJsonObject { put("note", "hello") },
                )
                assertEquals(HttpStatusCode.Accepted, response.status)
                val accepted = Json.parseToJsonElement(response.bodyAsText()).jsonObject
                val taskId = accepted["taskId"]!!.jsonPrimitive.content
                val trace = accepted["trace"]!!.jsonPrimitive.content
                assertEquals("delivered", accepted["state"]!!.jsonPrimitive.content)

                val frame = deviceWs.expect("diag.ping")
                assertEquals("server", frame.from)
                assertEquals(device, frame.to)
                assertEquals(taskId, frame.task)
                assertEquals(trace, frame.trace)
                assertEquals(buildJsonObject { put("note", "hello") }, frame.body)

                deviceWs.send(
                    Envelope(
                        v = 1,
                        id = UUID.randomUUID().toString(),
                        type = "task.result",
                        ts = System.currentTimeMillis(),
                        to = "server",
                        task = taskId,
                        body = buildJsonObject { put("output", "ok") },
                    ),
                )
                assertEquals("done", awaitTaskState(household, taskId, "done"))

                val audits = diagEvents()
                assertEquals(1, audits.size)
                val fields = audits.single().second
                assertEquals(
                    setOf("admin_id", "household_id", "device_id", "task_id", "trace"),
                    fields.keys,
                )
                assertEquals(admin.id, fields["admin_id"])
                assertEquals(household, fields["household_id"])
                assertEquals(device, fields["device_id"])
                assertEquals(taskId, fields["task_id"])
                assertEquals(trace, fields["trace"])
                assertTrue(fcm.requests.isEmpty(), "an online device must not be woken")
                deviceWs.close()
            } finally {
                fcm.close()
            }
        }

    @Test
    fun `an offline device is queued and woken once with high priority`() = testApplication {
        val fcm = FakeFcm()
        try {
            val admin = seedAdmin()
            val household = newHousehold()
            val device = newDevice()
            seedHousehold(db, household, device, UUID.randomUUID().toString())
            setPushToken(household, device, "fcm-device")
            application { module(config(fcm.push())) }

            val response = client.diag(
                admin.token,
                device,
                household,
                "diag.ping",
                buildJsonObject { put("note", "hello") },
            )
            assertEquals(HttpStatusCode.Accepted, response.status)
            val accepted = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val taskId = accepted["taskId"]!!.jsonPrimitive.content
            val trace = accepted["trace"]!!.jsonPrimitive.content
            assertEquals("queued", accepted["state"]!!.jsonPrimitive.content)
            assertEquals("queued", taskState(household, taskId))

            assertEquals(1, fcm.requests.size)
            val message = Json.parseToJsonElement(fcm.requests.single())
                .jsonObject["message"]!!.jsonObject
            assertEquals("fcm-device", message["token"]!!.jsonPrimitive.content)
            assertEquals("high", message["android"]!!.jsonObject["priority"]!!.jsonPrimitive.content)
            val data = message["data"]!!.jsonObject
            assertEquals(setOf("taskId", "traceId"), data.keys)
            assertEquals(taskId, data["taskId"]!!.jsonPrimitive.content)
            assertEquals(TraceIds.traceId(trace), data["traceId"]!!.jsonPrimitive.content)
            assertFalse(fcm.requests.single().contains("hello"))
        } finally {
            fcm.close()
        }
    }

    @Test
    fun `a revoked device is E_TENANT and leaves no task`() = testApplication {
        val admin = seedAdmin()
        val household = newHousehold()
        val device = newDevice()
        seedHousehold(db, household, device, UUID.randomUUID().toString())
        revoke(household, device)
        application { module(config()) }

        val response = client.diag(admin.token, device, household, "diag.reset")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("E_TENANT", code(response.bodyAsText()))
        assertEquals(0, taskCount(household))
    }

    @Test
    fun `a device in another household is E_TENANT`() = testApplication {
        val admin = seedAdmin()
        val household = newHousehold()
        val device = newDevice()
        seedHousehold(db, household, device, UUID.randomUUID().toString())
        val other = newHousehold()
        application { module(config()) }

        val response = client.diag(admin.token, device, other, "diag.ping")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("E_TENANT", code(response.bodyAsText()))
        assertEquals(0, taskCount(other))
    }

    @Test
    fun `smuggling task create through diag is E_SCHEMA`() = testApplication {
        val admin = seedAdmin()
        val household = newHousehold()
        val device = newDevice()
        seedHousehold(db, household, device, UUID.randomUUID().toString())
        application { module(config()) }

        val response = client.diag(admin.token, device, household, "task.create")

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("E_SCHEMA", code(response.bodyAsText()))
        assertEquals(0, taskCount(household))
    }

    @Test
    fun `diag state and diag reset are accepted and stored with their type`() = testApplication {
        val admin = seedAdmin()
        val household = newHousehold()
        val stateDevice = newDevice()
        seedHousehold(db, household, stateDevice, UUID.randomUUID().toString())
        val otherDevice = extraDevice(household, memberOf(household, stateDevice))
        application { module(config()) }

        for ((device, type) in listOf(stateDevice to "diag.state", otherDevice to "diag.reset")) {
            val response = client.diag(admin.token, device, household, type)
            assertEquals(HttpStatusCode.Accepted, response.status)
            val taskId = Json.parseToJsonElement(response.bodyAsText())
                .jsonObject["taskId"]!!.jsonPrimitive.content
            assertEquals(type, taskType(household, taskId))
        }
    }

    @Test
    fun `diag delivery and wake leave spans that resolve by trace`() = testApplication {
        val admin = seedAdmin()
        val household = newHousehold()
        val online = newDevice()
        seedHousehold(db, household, online, UUID.randomUUID().toString())
        val offline = extraDevice(household, memberOf(household, online))
        setPushToken(household, offline, "fcm-offline")
        application { module(config(PushPort { _, _, _, _ -> PushOutcome.Delivered })) }
        val onlineToken = accessToken(household, online)
        val onlineWs = connect(online, onlineToken)

        val delivered = Json.parseToJsonElement(
            client.diag(admin.token, online, household, "diag.ping").bodyAsText(),
        ).jsonObject
        val onlineTrace = delivered["trace"]!!.jsonPrimitive.content
        val onlineTraceId = TraceIds.traceId(onlineTrace)
        assertNotNull(onlineTraceId, "the admin response must carry a traceparent: $onlineTrace")
        val onlineSpans = traceStore.spans(household, onlineTraceId!!)
        assertTrue(
            onlineSpans.any { it.operation == "deliver" && it.deviceId == online },
            "deliver span with the target device id expected, got $onlineSpans",
        )
        onlineWs.expect("diag.ping")

        val resolved = client.get("/v1/traces/$onlineTraceId") {
            header("Authorization", "Bearer $onlineToken")
        }
        assertEquals(HttpStatusCode.OK, resolved.status)
        assertTrue(resolved.bodyAsText().contains("\"deliver\""))

        val queued = Json.parseToJsonElement(
            client.diag(admin.token, offline, household, "diag.reset").bodyAsText(),
        ).jsonObject
        val offlineTrace = queued["trace"]!!.jsonPrimitive.content
        val offlineSpans = traceStore.spans(household, TraceIds.traceId(offlineTrace)!!)
        assertTrue(
            offlineSpans.any { it.operation == "wake" && it.deviceId == offline },
            "wake span with the target device id expected, got $offlineSpans",
        )
        onlineWs.close()
    }

    @Test
    fun `the diag audit is durable when the wake fails`() = testApplication {
        val admin = seedAdmin()
        val household = newHousehold()
        val device = newDevice()
        seedHousehold(db, household, device, UUID.randomUUID().toString())
        setPushToken(household, device, "fcm-device")
        application { module(config(PushPort { _, _, _, _ -> error("push is down") })) }

        val response = client.diag(admin.token, device, household, "diag.ping")

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(1, diagEvents().size, "the audit line must survive the failed wake")
        assertEquals(1, taskCount(household), "the queued row must survive the failed wake")
    }

    @Test
    fun `a client cannot route a diag frame`() = testApplication {
        val household = newHousehold()
        val device = newDevice()
        seedHousehold(db, household, device, UUID.randomUUID().toString())
        application { module(config()) }
        val ws = connect(device, accessToken(household, device))

        ws.send(
            Envelope(
                v = 1,
                id = UUID.randomUUID().toString(),
                type = "diag.ping",
                ts = System.currentTimeMillis(),
                from = device,
                to = device,
                body = buildJsonObject { put("note", "from a client") },
            ),
        )

        val error = ws.expect("error")
        assertEquals("E_UNSUPPORTED", error.body["code"]!!.jsonPrimitive.content)
        ws.expectNothing("diag.ping")
        assertEquals(0, taskCount(household), "a client diag frame must not create a task")
        ws.close()
    }

    @Test
    fun `an oversized diag body is E_SCHEMA`() = testApplication {
        val admin = seedAdmin()
        val household = newHousehold()
        val device = newDevice()
        seedHousehold(db, household, device, UUID.randomUUID().toString())
        application { module(config()) }

        val response = client.diag(
            admin.token,
            device,
            household,
            "diag.ping",
            buildJsonObject { put("blob", "a".repeat(33_000)) },
        )

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("E_SCHEMA", code(response.bodyAsText()))
        assertEquals(0, taskCount(household))
    }

    @Test
    fun `a directory answer from another household is still E_TENANT`() = testApplication {
        val admin = seedAdmin()
        val household = newHousehold()
        val device = newDevice()
        val foreign = newHousehold()
        val liar = object : DeviceDirectory {
            override fun lookup(deviceId: String, householdId: String) =
                DeviceIdentity(deviceId, foreign, UUID.randomUUID().toString())
        }
        application { module(config().copy(devices = liar)) }

        val response = client.diag(admin.token, device, household, "diag.ping")

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("E_TENANT", code(response.bodyAsText()))
        assertEquals(0, taskCount(household))
    }

    @Test
    fun `a queued diag is redelivered on hello with its type`() = testApplication {
        val admin = seedAdmin()
        val household = newHousehold()
        val device = newDevice()
        seedHousehold(db, household, device, UUID.randomUUID().toString())
        application { module(config()) }

        val response = client.diag(admin.token, device, household, "diag.ping")
        assertEquals(HttpStatusCode.Accepted, response.status)
        val taskId = Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["taskId"]!!.jsonPrimitive.content

        val ws = connect(device, accessToken(household, device))

        val frame = ws.expect("diag.ping")
        assertEquals("diag.ping", frame.type)
        assertEquals(taskId, frame.task)
        assertEquals("server", frame.from)
        ws.close()
    }

    @Test
    fun `the validator accepts the diag types and still requires a target`() {
        assertTrue(EnvelopeValidator.TYPES.containsAll(setOf("diag.ping", "diag.state", "diag.reset")))
        val base = Envelope(
            v = 1,
            id = UUID.randomUUID().toString(),
            type = "diag.ping",
            ts = 1,
            from = "server",
        )
        assertEquals(
            "E_SCHEMA",
            EnvelopeValidator.validate(GatewayCodec.encode(base), base)?.code,
        )
        val addressed = base.copy(to = newDevice())
        assertNull(EnvelopeValidator.validate(GatewayCodec.encode(addressed), addressed))
    }
}

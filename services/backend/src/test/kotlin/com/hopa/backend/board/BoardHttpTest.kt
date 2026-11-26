package com.hopa.backend.board

import com.hopa.backend.ServerConfig
import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.board.BoardHouseholds.CHILD
import com.hopa.backend.board.BoardHouseholds.PARENT
import com.hopa.backend.harness.MapAuth
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.module
import com.hopa.backend.store.PgBoardStore
import com.hopa.backend.store.PgDevices
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** `GET /v1/board/ops`, `GET /v1/board/head` and `PUT /v1/board/cursor` on Postgres (B4.1). */
class BoardHttpTest {
    private val db = PostgresHarness.appDatabase()
    private val store = PgBoardStore(db)
    private val service = BoardService(store)

    /** Parent (holds the parents key at epoch 1), parent, teen. */
    private fun household(): List<DeviceIdentity> {
        val family = BoardHouseholds.seed(listOf(PARENT, PARENT, CHILD))
        store.putWrap(family[0].householdId, BoardScopes.PARENTS, 1, family[0].deviceId, ByteArray(60), null)
        return family
    }

    private fun config(family: List<DeviceIdentity>) = ServerConfig(
        version = "0.1.0",
        auth = MapAuth(family.associateBy { "tok-${it.deviceId}" }),
        devices = PgDevices(db),
        boardStore = store,
    )

    private fun append(device: DeviceIdentity, scope: String, ct: ByteArray = BoardFrames.ciphertext()): String {
        val opId = UUID.randomUUID().toString()
        val epoch = if (scope == BoardScopes.FAMILY) 0 else 1
        val result = service.append(device, opId, scope, epoch, ct)
        check(result is BoardResult.Ok) { "append failed: $result" }
        return opId
    }

    private suspend fun HttpClient.getAs(device: DeviceIdentity?, path: String): HttpResponse =
        get(path) { if (device != null) header("Authorization", "Bearer tok-${device.deviceId}") }

    private suspend fun HttpClient.putCursor(device: DeviceIdentity?, body: String): HttpResponse =
        put("/v1/board/cursor") {
            if (device != null) header("Authorization", "Bearer tok-${device.deviceId}")
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    @Test
    fun `ops are pulled with the attested actor and the stored ciphertext`() = testApplication {
        val family = household()
        val (mom, dad) = family
        application { module(config(family)) }
        val ct = BoardFrames.ciphertext(80)
        val opId = append(mom, BoardScopes.FAMILY, ct)

        val response = client.getAs(dad, "/v1/board/ops?scope=family&after=0")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.json()
        assertEquals("family", body["scope"]!!.jsonPrimitive.content)
        assertFalse(body["hasMore"]!!.jsonPrimitive.boolean)
        val op = body["ops"]!!.jsonArray.single().jsonObject
        assertEquals(1L, op["seq"]!!.jsonPrimitive.long)
        assertEquals(opId, op["opId"]!!.jsonPrimitive.content)
        assertEquals(mom.deviceId, op["fromDevice"]!!.jsonPrimitive.content)
        assertEquals(mom.memberId, op["memberId"]!!.jsonPrimitive.content)
        assertEquals(0, op["keyEpoch"]!!.jsonPrimitive.int)
        assertEquals(Base64.getEncoder().encodeToString(ct), op["ct"]!!.jsonPrimitive.content)
        assertTrue(op["at"]!!.jsonPrimitive.content.endsWith("Z"))
    }

    @Test
    fun `every board route answers 401 without a known token`() = testApplication {
        val family = household()
        application { module(config(family)) }
        val stranger = DeviceIdentity(UUID.randomUUID().toString(), family[0].householdId, family[0].memberId)

        for (device in listOf(null, stranger)) {
            assertEquals(HttpStatusCode.Unauthorized, client.getAs(device, "/v1/board/ops?scope=family").status)
            assertEquals(HttpStatusCode.Unauthorized, client.getAs(device, "/v1/board/head").status)
            val cursor = client.putCursor(device, """{"scope":"family","seq":1}""")
            assertEquals(HttpStatusCode.Unauthorized, cursor.status)
            assertEquals("E_AUTH", cursor.json()["code"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun `a device without the parents key is refused on every parents route`() = testApplication {
        val family = household()
        val (mom, dad, teen) = family
        application { module(config(family)) }
        append(mom, BoardScopes.PARENTS)
        append(mom, BoardScopes.FAMILY)

        for (device in listOf(dad, teen)) {
            val ops = client.getAs(device, "/v1/board/ops?scope=parents")
            assertEquals(HttpStatusCode.Forbidden, ops.status)
            assertEquals("E_SCOPE", ops.json()["code"]!!.jsonPrimitive.content)
            val cursor = client.putCursor(device, """{"scope":"parents","seq":1}""")
            assertEquals(HttpStatusCode.Forbidden, cursor.status)
            assertEquals("E_SCOPE", cursor.json()["code"]!!.jsonPrimitive.content)
            val heads = client.getAs(device, "/v1/board/head").json()["heads"]!!.jsonObject
            assertEquals(mapOf("family" to 2L), heads.mapValues { it.value.jsonPrimitive.long })
        }
        val momHeads = client.getAs(mom, "/v1/board/head").json()["heads"]!!.jsonObject
        assertEquals(mapOf("family" to 2L, "parents" to 1L), momHeads.mapValues { it.value.jsonPrimitive.long })
        assertEquals(1, client.getAs(mom, "/v1/board/ops?scope=parents").json()["ops"]!!.jsonArray.size)
    }

    @Test
    fun `paging returns every op once, in order`() = testApplication {
        val family = household()
        val (mom, dad) = family
        application { module(config(family)) }
        val appended = (1..7).map { append(if (it % 2 == 0) dad else mom, BoardScopes.FAMILY) }
        val seen = mutableListOf<String>()
        var after = 0L
        var pages = 0

        do {
            val page = client.getAs(dad, "/v1/board/ops?scope=family&after=$after&limit=3").json()
            val ops = page["ops"]!!.jsonArray.map { it.jsonObject }
            seen += ops.map { it["opId"]!!.jsonPrimitive.content }
            after = ops.lastOrNull()?.get("seq")?.jsonPrimitive?.long ?: after
            pages++
        } while (page["hasMore"]!!.jsonPrimitive.boolean)

        assertEquals(appended, seen)
        assertEquals(3, pages)
        assertTrue(client.getAs(dad, "/v1/board/ops?scope=family&after=7").json()["ops"]!!.jsonArray.isEmpty())
    }

    @Test
    fun `malformed queries and bodies are E_SCHEMA`() = testApplication {
        val family = household()
        val mom = family[0]
        application { module(config(family)) }

        for (query in listOf("", "?scope=school", "?scope=family&after=-1", "?scope=family&after=x", "?scope=family&limit=0", "?scope=family&limit=x")) {
            val response = client.getAs(mom, "/v1/board/ops$query")
            assertEquals(HttpStatusCode.BadRequest, response.status, query)
            assertEquals("E_SCHEMA", response.json()["code"]!!.jsonPrimitive.content, query)
        }
        for (body in listOf("{}", "not json", """{"scope":"family","seq":-1}""", """{"scope":"school","seq":1}""")) {
            val response = client.putCursor(mom, body)
            assertEquals(HttpStatusCode.BadRequest, response.status, body)
            assertEquals("E_SCHEMA", response.json()["code"]!!.jsonPrimitive.content, body)
        }
    }

    @Test
    fun `a cursor is recorded per device and scope`() = testApplication {
        val family = household()
        val (mom, dad) = family
        application { module(config(family)) }

        assertEquals(HttpStatusCode.NoContent, client.putCursor(mom, """{"scope":"family","seq":5}""").status)
        assertEquals(HttpStatusCode.NoContent, client.putCursor(mom, """{"scope":"parents","seq":3}""").status)
        assertEquals(HttpStatusCode.NoContent, client.putCursor(mom, """{"scope":"family","seq":6}""").status)

        assertEquals(mapOf("family" to 6L, "parents" to 3L), store.cursors(mom.householdId, mom.deviceId))
        assertEquals(emptyMap<String, Long>(), store.cursors(dad.householdId, dad.deviceId))
    }
}

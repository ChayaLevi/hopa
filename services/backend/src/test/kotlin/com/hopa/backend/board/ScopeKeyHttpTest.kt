package com.hopa.backend.board

import com.hopa.backend.ServerConfig
import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.harness.MapAuth
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.module
import com.hopa.backend.store.PgBoardStore
import com.hopa.backend.store.PgDevices
import io.ktor.client.HttpClient
import io.ktor.client.request.get
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** `GET|POST /v1/scope-keys/{scope}`: the holder and bootstrap rules on Postgres (B4.2). */
class ScopeKeyHttpTest {
    private val db = PostgresHarness.appDatabase()
    private val store = PgBoardStore(db)
    private val devices = PgDevices(db)

    private fun config(family: List<DeviceIdentity>) = ServerConfig(
        version = "0.1.0",
        auth = MapAuth(family.associateBy { "tok-${it.deviceId}" }),
        devices = devices,
        boardStore = store,
    )

    private fun wrap(): ByteArray = Random.nextBytes(60)

    private suspend fun HttpClient.wrapFor(
        caller: DeviceIdentity,
        target: String,
        epoch: Int,
        wrapped: ByteArray = wrap(),
        scope: String = "parents",
    ): HttpResponse = post("/v1/scope-keys/$scope") {
        header("Authorization", "Bearer tok-${caller.deviceId}")
        contentType(ContentType.Application.Json)
        setBody(
            """{"deviceId":"$target","wrappedKey":"${Base64.getUrlEncoder().withoutPadding().encodeToString(wrapped)}","keyEpoch":$epoch}""",
        )
    }

    private suspend fun HttpClient.keyOf(device: DeviceIdentity, scope: String = "parents"): HttpResponse =
        get("/v1/scope-keys/$scope") { header("Authorization", "Bearer tok-${device.deviceId}") }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private fun JsonObject.code(): String = this["code"]!!.jsonPrimitive.content

    @Test
    fun `a parent device bootstraps the key for itself and reads it back`() = testApplication {
        val family = BoardHouseholds.seed(listOf("parent", "parent"))
        val mom = family[0]
        val momPublic = Random.nextBytes(32)
        BoardHouseholds.setPublicKey(mom, momPublic)
        application { module(config(family)) }

        val before = client.keyOf(mom)
        assertEquals(HttpStatusCode.NotFound, before.status)
        val missing = before.json()
        assertEquals("E_SCOPE", missing.code())
        assertEquals(JsonNull, missing["currentEpoch"] ?: JsonNull)
        assertEquals(1, missing["bootstrapEpoch"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, missing["lostEpoch"] ?: JsonNull)

        val wrapped = wrap()
        val created = client.wrapFor(mom, mom.deviceId, 1, wrapped)
        assertEquals(HttpStatusCode.Created, created.status)
        val stored = created.json()
        assertTrue(stored["bootstrap"]!!.jsonPrimitive.boolean)
        assertEquals(1, stored["keyEpoch"]!!.jsonPrimitive.int)

        val read = client.keyOf(mom).json()
        assertEquals(1, read["keyEpoch"]!!.jsonPrimitive.int)
        assertEquals(mom.deviceId, read["wrapperDeviceId"]!!.jsonPrimitive.content)
        assertTrue(Base64.getUrlDecoder().decode(read["wrappedKey"]!!.jsonPrimitive.content).contentEquals(wrapped))
        assertTrue(Base64.getUrlDecoder().decode(read["wrapperPublicKey"]!!.jsonPrimitive.content).contentEquals(momPublic))
        assertEquals(1, store.heldEpoch(mom.householdId, mom.deviceId, BoardScopes.PARENTS))
    }

    @Test
    fun `of two concurrent bootstraps over HTTP one wins`() = testApplication {
        val family = BoardHouseholds.seed(listOf("parent", "parent"))
        val (mom, dad) = family
        application { module(config(family)) }

        val answers = coroutineScope {
            listOf(mom, dad).map { device -> async { client.wrapFor(device, device.deviceId, 1).status } }.awaitAll()
        }

        assertEquals(listOf(HttpStatusCode.Conflict, HttpStatusCode.Created), answers.sortedBy { it.value }.reversed())
        val holders = listOf(mom, dad).count { store.heldEpoch(it.householdId, it.deviceId, BoardScopes.PARENTS) != null }
        assertEquals(1, holders)
    }

    @Test
    fun `of many concurrent bootstraps in the store exactly one is stored`() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            repeat(10) { round ->
                val parents = BoardHouseholds.seed(List(4) { "parent" })
                val start = CountDownLatch(1)
                val outcomes = parents.map { device ->
                    pool.submit<WrapOutcome> {
                        start.await()
                        store.writeWrap(
                            device.householdId,
                            WrapRequest(BoardScopes.PARENTS, device.deviceId, device.deviceId, 1, wrap()),
                        )
                    }
                }
                start.countDown()
                val results = outcomes.map { it.get(30, TimeUnit.SECONDS) }
                assertEquals(1, results.count { it is WrapOutcome.Stored }, "round $round: $results")
                assertTrue(results.filterIsInstance<WrapOutcome.Refused>().all { it.error.code == "E_CONFLICT" })
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `a holder wraps for another parent, who then reads the wrap from the holder`() = testApplication {
        val family = BoardHouseholds.seed(listOf("parent", "parent"))
        val (mom, dad) = family
        application { module(config(family)) }
        client.wrapFor(mom, mom.deviceId, 1)

        val shared = client.wrapFor(mom, dad.deviceId, 1)

        assertEquals(HttpStatusCode.Created, shared.status)
        assertEquals(false, shared.json()["bootstrap"]!!.jsonPrimitive.boolean)
        val read = client.keyOf(dad).json()
        assertEquals(1, read["keyEpoch"]!!.jsonPrimitive.int)
        assertEquals(mom.deviceId, read["wrapperDeviceId"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a parent device without the key cannot hand it out`() = testApplication {
        val family = BoardHouseholds.seed(listOf("parent", "parent", "parent"))
        val (mom, dad, grandma) = family
        application { module(config(family)) }
        client.wrapFor(mom, mom.deviceId, 1)

        val refused = client.wrapFor(dad, grandma.deviceId, 1)
        assertEquals(HttpStatusCode.Forbidden, refused.status)
        assertEquals("E_SCOPE", refused.json().code())

        val late = client.wrapFor(dad, dad.deviceId, 1)
        assertEquals(HttpStatusCode.Conflict, late.status)
        assertEquals("E_CONFLICT", late.json().code())
        assertNull(store.heldEpoch(grandma.householdId, grandma.deviceId, BoardScopes.PARENTS))
        assertNull(store.heldEpoch(dad.householdId, dad.deviceId, BoardScopes.PARENTS))
    }

    @Test
    fun `the key never goes to a teen device, and a teen cannot create one`() = testApplication {
        val family = BoardHouseholds.seed(listOf("parent", "teen", null))
        val (mom, teen, unknownRole) = family
        application { module(config(family)) }

        assertEquals("E_SCOPE", client.wrapFor(teen, teen.deviceId, 1).json().code())
        client.wrapFor(mom, mom.deviceId, 1)

        for (target in listOf(teen, unknownRole)) {
            val refused = client.wrapFor(mom, target.deviceId, 1)
            assertEquals(HttpStatusCode.Forbidden, refused.status)
            assertEquals("E_SCOPE", refused.json().code())
            assertNull(store.heldEpoch(target.householdId, target.deviceId, BoardScopes.PARENTS))
        }
        val teenRead = client.keyOf(teen)
        assertEquals(HttpStatusCode.NotFound, teenRead.status)
        assertEquals(1, teenRead.json()["currentEpoch"]!!.jsonPrimitive.int)
    }

    @Test
    fun `a stale epoch, another household's device and malformed requests are refused`() = testApplication {
        val family = BoardHouseholds.seed(listOf("parent", "parent"))
        val (mom, dad) = family
        val stranger = BoardHouseholds.seed(listOf("parent")).single()
        application { module(config(family + stranger)) }

        assertEquals(HttpStatusCode.Conflict, client.wrapFor(mom, mom.deviceId, 2).status, "a bootstrap starts at 1")
        client.wrapFor(mom, mom.deviceId, 1)
        assertEquals(HttpStatusCode.Conflict, client.wrapFor(mom, dad.deviceId, 2).status, "the current epoch is 1")

        val foreign = client.wrapFor(mom, stranger.deviceId, 1)
        assertEquals(HttpStatusCode.NotFound, foreign.status)
        assertEquals("E_TENANT", foreign.json().code())
        assertNull(store.heldEpoch(stranger.householdId, stranger.deviceId, BoardScopes.PARENTS))

        assertEquals(HttpStatusCode.BadRequest, client.wrapFor(mom, dad.deviceId, 1, Random.nextBytes(59)).status)
        assertEquals(HttpStatusCode.BadRequest, client.wrapFor(mom, "not-a-uuid", 1).status)
        assertEquals(HttpStatusCode.BadRequest, client.wrapFor(mom, dad.deviceId, 0).status)
        assertEquals(HttpStatusCode.BadRequest, client.wrapFor(mom, dad.deviceId, 1, scope = "family").status)
        assertEquals(HttpStatusCode.BadRequest, client.keyOf(mom, scope = "family").status)
        val noAuth = client.get("/v1/scope-keys/parents")
        assertEquals(HttpStatusCode.Unauthorized, noAuth.status)
    }

    @Test
    fun `when every wrap is gone a new bootstrap creates the next epoch and says what is lost`() = testApplication {
        val family = BoardHouseholds.seed(listOf("parent", "parent"))
        val (mom, dad) = family
        application { module(config(family)) }
        client.wrapFor(mom, mom.deviceId, 1)
        val op = BoardService(store).append(mom, java.util.UUID.randomUUID().toString(), BoardScopes.PARENTS, 1, BoardFrames.ciphertext())
        assertTrue(op is BoardResult.Ok)

        devices.revokeDevice(mom.deviceId, mom.householdId)

        val missing = client.keyOf(dad).json()
        assertEquals(JsonNull, missing["currentEpoch"] ?: JsonNull)
        assertEquals(2, missing["bootstrapEpoch"]!!.jsonPrimitive.int)
        assertEquals(1, missing["lostEpoch"]!!.jsonPrimitive.int)
        assertEquals(HttpStatusCode.Conflict, client.wrapFor(dad, dad.deviceId, 1).status, "epoch 1 is spent")

        val reborn = client.wrapFor(dad, dad.deviceId, 2)
        assertEquals(HttpStatusCode.Created, reborn.status)
        val stored = reborn.json()
        assertTrue(stored["bootstrap"]!!.jsonPrimitive.boolean)
        assertEquals(1, stored["lostEpoch"]!!.jsonPrimitive.int)
        assertEquals(2, store.heldEpoch(dad.householdId, dad.deviceId, BoardScopes.PARENTS))
    }
}

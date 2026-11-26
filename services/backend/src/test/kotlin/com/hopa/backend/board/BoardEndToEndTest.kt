package com.hopa.backend.board

import com.hopa.backend.ServerConfig
import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.board.BoardFrames.online
import com.hopa.backend.board.BoardFrames.op
import com.hopa.backend.crypto.KeyWrap
import com.hopa.backend.harness.MapAuth
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.module
import com.hopa.backend.pairing.X25519Keys
import com.hopa.backend.store.PgBoardStore
import com.hopa.backend.store.PgDevices
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
import io.ktor.server.testing.testApplication
import java.util.Base64
import java.util.UUID
import javax.crypto.AEADBadTagException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The whole board flow with real crypto (B4.5): two parent devices share the
 * parents key through KeyWrap and the scope-key routes, ops are sealed with
 * the D13 AAD and appended over the gateway, and each device decrypts what it
 * pulls. A teen device gets only `family`. A ciphertext replayed under another
 * op id is stored (the server cannot tell) but no device can open it.
 *
 * KeyWrap still uses ADR-0026's HKDF label; the scope label
 * (`hopa.scope.key.wrap.v1`, ADR-0033 §3) lands with the client core in phase C.
 */
class BoardEndToEndTest {
    private val db = PostgresHarness.appDatabase()
    private val store = PgBoardStore(db)

    private class Keys(val publicKey: ByteArray, val privateKey: ByteArray)

    private fun keysFor(device: DeviceIdentity): Keys {
        val pair = X25519Keys.generate()
        val keys = Keys(X25519Keys.publicKeyBytes(pair.public), pair.private.encoded)
        BoardHouseholds.setPublicKey(device, keys.publicKey)
        return keys
    }

    private fun b64(bytes: ByteArray) = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private suspend fun HttpClient.wrapFor(caller: DeviceIdentity, target: DeviceIdentity, wrapped: ByteArray): HttpResponse =
        post("/v1/scope-keys/parents") {
            header("Authorization", "Bearer tok-${caller.deviceId}")
            contentType(ContentType.Application.Json)
            setBody("""{"deviceId":"${target.deviceId}","wrappedKey":"${b64(wrapped)}","keyEpoch":1}""")
        }

    private suspend fun HttpClient.json(device: DeviceIdentity, path: String): Pair<HttpStatusCode, JsonObject> {
        val response = get(path) { header("Authorization", "Bearer tok-${device.deviceId}") }
        return response.status to Json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    /** Pulls every op of [scope] and opens each with [key] and the AAD of its own place. */
    private suspend fun HttpClient.pullAndOpen(device: DeviceIdentity, scope: String, key: ByteArray): Map<String, Result<String>> {
        val (status, body) = json(device, "/v1/board/ops?scope=$scope")
        assertEquals(HttpStatusCode.OK, status)
        return body["ops"]!!.jsonArray.map { it.jsonObject }.associate { op ->
            val opId = op["opId"]!!.jsonPrimitive.content
            val aad = BoardTestCipher.aad(device.householdId, scope, opId, op["keyEpoch"]!!.jsonPrimitive.int)
            val ct = Base64.getDecoder().decode(op["ct"]!!.jsonPrimitive.content)
            opId to runCatching { BoardTestCipher.open(key, aad, ct).toString(Charsets.UTF_8) }
        }
    }

    @Test
    fun `parents share a key, devices decrypt what they pull, a teen sees only family`() = testApplication {
        val family = BoardHouseholds.seed(listOf("parent", "parent", "teen"))
        val (mom, dad, teen) = family
        val momKeys = keysFor(mom)
        val dadKeys = keysFor(dad)
        keysFor(teen)
        val householdKey = KeyWrap.generateContentKey() // ADR-0026's key, shared at pairing
        application {
            module(
                ServerConfig(
                    version = "0.1.0",
                    auth = MapAuth(family.associateBy { "tok-${it.deviceId}" }),
                    devices = PgDevices(db),
                    boardStore = store,
                ),
            )
        }

        // Mom bootstraps the parents key and wraps it for dad's verified key.
        val parentsKey = KeyWrap.generateContentKey()
        val bootstrap = client.wrapFor(mom, mom, KeyWrap.wrapForDevice(parentsKey, momKeys.publicKey, momKeys.privateKey))
        assertEquals(HttpStatusCode.Created, bootstrap.status)
        val shared = client.wrapFor(mom, dad, KeyWrap.wrapForDevice(parentsKey, dadKeys.publicKey, momKeys.privateKey))
        assertEquals(HttpStatusCode.Created, shared.status)

        // Dad unwraps it with the wrapper's public key the server returns.
        val (keyStatus, wrap) = client.json(dad, "/v1/scope-keys/parents")
        assertEquals(HttpStatusCode.OK, keyStatus)
        val dadParentsKey = KeyWrap.unwrapByDevice(
            Base64.getUrlDecoder().decode(wrap["wrappedKey"]!!.jsonPrimitive.content),
            Base64.getUrlDecoder().decode(wrap["wrapperPublicKey"]!!.jsonPrimitive.content),
            dadKeys.privateKey,
        )
        assertTrue(dadParentsKey.contentEquals(parentsKey))

        // Mom appends one op in each scope over the gateway.
        val http = createClient { install(WebSockets) }
        val momSocket = online(http, mom.deviceId)
        val household = mom.householdId
        val parentsOp = UUID.randomUUID().toString()
        val parentsCt = BoardTestCipher.seal(
            parentsKey,
            BoardTestCipher.aad(household, BoardScopes.PARENTS, parentsOp, 1),
            "a surprise party for the teen on Friday".toByteArray(),
        )
        momSocket.send(op(BoardScopes.PARENTS, keyEpoch = 1, ct = parentsCt, opId = parentsOp))
        momSocket.expect("board.op.ack")
        val familyOp = UUID.randomUUID().toString()
        val familyCt = BoardTestCipher.seal(
            householdKey,
            BoardTestCipher.aad(household, BoardScopes.FAMILY, familyOp, 0),
            "buy milk".toByteArray(),
        )
        momSocket.send(op(BoardScopes.FAMILY, ct = familyCt, opId = familyOp))
        momSocket.expect("board.op.ack")

        assertEquals("a surprise party for the teen on Friday", client.pullAndOpen(dad, "parents", dadParentsKey)[parentsOp]!!.getOrThrow())
        assertEquals("buy milk", client.pullAndOpen(dad, "family", householdKey)[familyOp]!!.getOrThrow())
        assertEquals("buy milk", client.pullAndOpen(teen, "family", householdKey)[familyOp]!!.getOrThrow())
        assertEquals(HttpStatusCode.Forbidden, client.json(teen, "/v1/board/ops?scope=parents").first)
        assertEquals(HttpStatusCode.NotFound, client.json(teen, "/v1/scope-keys/parents").first)

        // The same ciphertext under another op id: stored, but it opens nowhere.
        val replayOp = UUID.randomUUID().toString()
        momSocket.send(op(BoardScopes.PARENTS, keyEpoch = 1, ct = parentsCt, opId = replayOp))
        momSocket.expect("board.op.ack")
        val opened = client.pullAndOpen(dad, "parents", dadParentsKey)
        assertTrue(opened[parentsOp]!!.isSuccess)
        assertThrows(AEADBadTagException::class.java) { opened[replayOp]!!.getOrThrow() }
        // Nor under the other scope's key and place.
        val crossScope = runCatching {
            BoardTestCipher.open(householdKey, BoardTestCipher.aad(household, BoardScopes.FAMILY, parentsOp, 0), parentsCt)
        }
        assertTrue(crossScope.exceptionOrNull() is AEADBadTagException)
    }
}

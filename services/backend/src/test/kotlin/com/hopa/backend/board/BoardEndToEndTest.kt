package com.hopa.backend.board

import com.hopa.backend.ServerConfig
import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.board.BoardFrames.online
import com.hopa.backend.board.client.AppendResult
import com.hopa.backend.board.client.BoardCipher
import com.hopa.backend.board.client.BoardHttp
import com.hopa.backend.board.client.BoardLink
import com.hopa.backend.board.client.HttpAnswer
import com.hopa.backend.board.client.ScopeKeyAnswer
import com.hopa.backend.board.client.ScopeKeys
import com.hopa.backend.board.client.Unwrapped
import com.hopa.backend.board.client.VerifiedDevice
import com.hopa.backend.crypto.KeyWrap
import com.hopa.backend.harness.FakeDevice
import com.hopa.backend.harness.MapAuth
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.module
import com.hopa.backend.pairing.X25519Keys
import com.hopa.backend.protocol.Envelope
import com.hopa.backend.store.PgBoardStore
import com.hopa.backend.store.PgDevices
import com.hopa.backend.wire.BackendEnvelope
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.util.UUID
import javax.crypto.AEADBadTagException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The whole board flow with the client core's own pieces (B4.5, switched to them in C4): the parents
 * key is wrapped with `ScopeKeys` (KeyWrap under `hopa.scope.key.wrap.v1`, only for verified keys,
 * D16), ops are sealed with `BoardCipher` (the D13 AAD) and appended and pulled through `BoardLink`
 * against the real routes and gateway. A teen device gets only `family`. A ciphertext replayed under
 * another op id is stored (the server cannot tell) but no device can open it.
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

    /** The board link's HTTP port over the in-process test server, as one device. */
    private class TestHttp(private val client: HttpClient, private val device: DeviceIdentity) : BoardHttp {
        private suspend fun answer(response: HttpResponse) = HttpAnswer(response.status.value, response.bodyAsText())

        override suspend fun get(path: String) = answer(client.get(path) { auth() })

        override suspend fun post(path: String, json: String) = answer(
            client.post(path) {
                auth()
                contentType(ContentType.Application.Json)
                setBody(json)
            },
        )

        override suspend fun put(path: String, json: String) = answer(
            client.put(path) {
                auth()
                contentType(ContentType.Application.Json)
                setBody(json)
            },
        )

        private fun io.ktor.client.request.HttpRequestBuilder.auth() = header("Authorization", "Bearer tok-${device.deviceId}")
    }

    private fun toServer(frame: BackendEnvelope) =
        Envelope(frame.v, frame.id, frame.type, frame.ts, frame.trace, frame.from, frame.to, frame.task, frame.body)

    private fun toClient(frame: Envelope) =
        BackendEnvelope(frame.v, frame.id, frame.type, frame.ts, frame.trace, frame.from, frame.to, frame.task, frame.body)

    /** Appends through [link] on [socket] and feeds the gateway's answer back to the link. */
    private suspend fun append(link: BoardLink, socket: FakeDevice, scope: String, opId: String, epoch: Int, ct: ByteArray) =
        coroutineScope {
            val result = async { link.append(scope, opId, epoch, ct) }
            link.receive(toClient(socket.expect("board.op.ack")))
            result.await()
        }

    /** Pulls every op of [scope] through [link] and opens each at its own place. */
    private suspend fun pullAndOpen(link: BoardLink, household: String, scope: String, key: ByteArray): Map<String, Result<String>> =
        link.pullAll(scope, after = 0).associate { op ->
            op.opId to runCatching {
                BoardCipher.open(key, BoardCipher.Place(household, scope, op.opId, op.keyEpoch), op.ct).toString(Charsets.UTF_8)
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
        val http = createClient { install(WebSockets) }
        val momSocket = online(http, mom.deviceId)
        val momLink = BoardLink(TestHttp(client, mom), send = { momSocket.send(toServer(it)) })
        val dadLink = BoardLink(TestHttp(client, dad), send = {})
        val teenLink = BoardLink(TestHttp(client, teen), send = {})

        // Mom verified both parents' keys (she admitted dad's device); dad verified hers (D16).
        val momScope = ScopeKeys(
            momKeys.privateKey,
            listOf(VerifiedDevice(mom.deviceId, momKeys.publicKey), VerifiedDevice(dad.deviceId, dadKeys.publicKey)),
        )
        val dadScope = ScopeKeys(dadKeys.privateKey, listOf(VerifiedDevice(mom.deviceId, momKeys.publicKey)))

        // Mom bootstraps the parents key and wraps it for dad.
        val bootstrap = momLink.scopeKey("parents") as ScopeKeyAnswer.Missing
        assertEquals(1, bootstrap.bootstrapEpoch)
        val parentsKey = momScope.newKey()
        assertEquals(201, momLink.putScopeKey("parents", mom.deviceId, momScope.wrapFor(mom.deviceId, parentsKey)!!, 1).status)
        assertEquals(201, momLink.putScopeKey("parents", dad.deviceId, momScope.wrapFor(dad.deviceId, parentsKey)!!, 1).status)

        // Dad unwraps it with the wrapper's public key the server returns, which he verified.
        val held = dadLink.scopeKey("parents") as ScopeKeyAnswer.Held
        val dadParentsKey = (dadScope.unwrap(held.wrappedKey, held.wrapperPublicKey!!) as Unwrapped.Key).key
        assertArrayEquals(parentsKey, dadParentsKey)

        // Mom appends one op in each scope over the gateway.
        val household = mom.householdId
        val parentsOp = UUID.randomUUID().toString()
        val parentsCt = BoardCipher.seal(
            parentsKey,
            BoardCipher.Place(household, BoardScopes.PARENTS, parentsOp, 1),
            "a surprise party for the teen on Friday".toByteArray(),
        )
        assertTrue(append(momLink, momSocket, BoardScopes.PARENTS, parentsOp, 1, parentsCt) is AppendResult.Acked)
        val familyOp = UUID.randomUUID().toString()
        val familyCt = BoardCipher.seal(householdKey, BoardCipher.Place(household, BoardScopes.FAMILY, familyOp, 0), "buy milk".toByteArray())
        assertTrue(append(momLink, momSocket, BoardScopes.FAMILY, familyOp, 0, familyCt) is AppendResult.Acked)

        assertEquals("a surprise party for the teen on Friday", pullAndOpen(dadLink, household, "parents", dadParentsKey)[parentsOp]!!.getOrThrow())
        assertEquals("buy milk", pullAndOpen(dadLink, household, "family", householdKey)[familyOp]!!.getOrThrow())
        assertEquals("buy milk", pullAndOpen(teenLink, household, "family", householdKey)[familyOp]!!.getOrThrow())
        assertEquals(HttpStatusCode.Forbidden.value, TestHttp(client, teen).get("/v1/board/ops?scope=parents").status)
        assertTrue(teenLink.scopeKey("parents") is ScopeKeyAnswer.Missing)

        // The same ciphertext under another op id: stored, but it opens nowhere.
        val replayOp = UUID.randomUUID().toString()
        assertTrue(append(momLink, momSocket, BoardScopes.PARENTS, replayOp, 1, parentsCt) is AppendResult.Acked)
        val opened = pullAndOpen(dadLink, household, "parents", dadParentsKey)
        assertTrue(opened[parentsOp]!!.isSuccess)
        assertThrows(AEADBadTagException::class.java) { opened[replayOp]!!.getOrThrow() }
        // Nor under the other scope's key and place.
        val crossScope = runCatching {
            BoardCipher.open(householdKey, BoardCipher.Place(household, BoardScopes.FAMILY, parentsOp, 0), parentsCt)
        }
        assertTrue(crossScope.exceptionOrNull() is AEADBadTagException)
    }
}

package com.hopa.backend.board

import com.hopa.backend.ServerConfig
import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.board.BoardFrames.online
import com.hopa.backend.board.BoardFrames.op
import com.hopa.backend.gateway.DeviceRegistry
import com.hopa.backend.harness.MapAuth
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.harness.bodyString
import com.hopa.backend.module
import com.hopa.backend.store.PgBoardStore
import com.hopa.backend.store.PgDevices
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Losing the parents scope (B4.4): a revoked device and a demoted parent lose
 * their wraps in the same transaction as the change, their sockets close, and
 * no `parents` op reaches them afterwards.
 */
class BoardRevocationTest {
    private val db = PostgresHarness.appDatabase()
    private val store = PgBoardStore(db)
    private val devices = PgDevices(db)

    /** Mom, dad (both holding the parents key at epoch 1) and a teen. */
    private fun household(): List<DeviceIdentity> {
        val family = BoardHouseholds.seed(listOf("parent", "parent", "teen"))
        family.take(2).forEach { store.putWrap(it.householdId, BoardScopes.PARENTS, 1, it.deviceId, ByteArray(60), it.deviceId) }
        return family
    }

    private fun config(family: List<DeviceIdentity>) = ServerConfig(
        version = "0.1.0",
        auth = MapAuth(family.associateBy { "tok-${it.deviceId}" }),
        devices = devices,
        boardStore = store,
    )

    private fun wrapRows(device: DeviceIdentity): Int = db.tenant(device.householdId) { connection ->
        connection.prepareStatement("select count(*) from scope_key_wraps where device_id = ?::uuid").use {
            it.setString(1, device.deviceId)
            it.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
    }

    @Test
    fun `a revoked device loses its scope wraps with its device row and its socket`() = testApplication {
        val family = household()
        val (mom, dad) = family
        application { module(config(family)) }
        val http = createClient { install(WebSockets) }
        val momSocket = online(http, mom.deviceId)
        val dadSocket = online(http, dad.deviceId)

        val revoked = client.delete("/v1/devices/${dad.deviceId}") { header("Authorization", "Bearer tok-${mom.deviceId}") }

        assertEquals(HttpStatusCode.NoContent, revoked.status)
        dadSocket.awaitClosed(5_000)
        assertEquals(0, wrapRows(dad))
        assertNull(store.heldEpoch(dad.householdId, dad.deviceId, BoardScopes.PARENTS))
        assertEquals(1, store.heldEpoch(mom.householdId, mom.deviceId, BoardScopes.PARENTS), "mom keeps the key")

        momSocket.send(op(BoardScopes.PARENTS))
        momSocket.expect("board.op.ack")
        // The test token map still knows dad: the scope rule alone must refuse him.
        val pulled = client.get("/v1/board/ops?scope=parents") { header("Authorization", "Bearer tok-${dad.deviceId}") }
        assertEquals(HttpStatusCode.Forbidden, pulled.status)
    }

    @Test
    fun `a parent who stops being a parent loses the key on every device, and hears no parents op`() = testApplication {
        val family = household()
        val (mom, dad) = family
        val config = config(family)
        application { module(config) }
        val http = createClient { install(WebSockets) }
        val momSocket = online(http, mom.deviceId)
        val dadSocket = online(http, dad.deviceId)
        val roles = FamilyRoles(store, config.registry)

        val demoted = roles.setFamilyRole(dad.householdId, dad.memberId, "teen")

        assertEquals(listOf(dad.deviceId), demoted)
        assertEquals(0, wrapRows(dad))
        dadSocket.awaitClosed(5_000)

        // Not a revocation: the device reconnects at once, with family rights only.
        val dadAgain = online(http, dad.deviceId)
        momSocket.send(op(BoardScopes.PARENTS))
        momSocket.expect("board.op.ack")
        dadAgain.expectNothing("board.changed", withinMs = 1_000)
        momSocket.send(op(BoardScopes.FAMILY))
        momSocket.expect("board.op.ack")
        assertEquals("family", dadAgain.expect("board.changed").bodyString("scope"))

        val pulled = client.get("/v1/board/ops?scope=parents") { header("Authorization", "Bearer tok-${dad.deviceId}") }
        assertEquals(HttpStatusCode.Forbidden, pulled.status)
        val outcome = store.writeWrap(dad.householdId, WrapRequest(BoardScopes.PARENTS, mom.deviceId, dad.deviceId, 1, ByteArray(60)))
        assertEquals("E_SCOPE", (outcome as WrapOutcome.Refused).error.code, "no new wrap for a teen")
    }

    @Test
    fun `a role change that keeps or grants parent closes nothing`() {
        val family = household()
        val (mom, _, teen) = family
        val registry = DeviceRegistry()
        val roles = FamilyRoles(store, registry)

        assertEquals(emptyList<String>(), roles.setFamilyRole(mom.householdId, mom.memberId, "parent"))
        assertEquals(emptyList<String>(), roles.setFamilyRole(teen.householdId, teen.memberId, "parent"))
        assertEquals(emptyList<String>(), roles.setFamilyRole(teen.householdId, "00000000-0000-0000-0000-000000000000", null))
        assertEquals(1, wrapRows(mom))
    }
}

package com.hopa.backend.board

import com.hopa.backend.ServerConfig
import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.board.BoardFrames.online
import com.hopa.backend.board.BoardFrames.op
import com.hopa.backend.board.BoardHouseholds.CHILD
import com.hopa.backend.board.BoardHouseholds.MANAGER
import com.hopa.backend.board.BoardHouseholds.PARENT
import com.hopa.backend.gateway.DeviceRegistry
import com.hopa.backend.harness.MapAuth
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.harness.bodyString
import com.hopa.backend.members.MemberService
import com.hopa.backend.members.PermissionOutcome
import com.hopa.backend.members.Permissions
import com.hopa.backend.module
import com.hopa.backend.store.PgBoardStore
import com.hopa.backend.store.PgDevices
import com.hopa.backend.store.PgMemberStore
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Losing the parents scope (B4.4, B5): a revoked device and a member who loses
 * `board.private` lose
 * their wraps in the same transaction as the change, their sockets close, and
 * no `parents` op reaches them afterwards.
 */
class BoardRevocationTest {
    private val db = PostgresHarness.appDatabase()
    private val store = PgBoardStore(db)
    private val devices = PgDevices(db)
    private val members = PgMemberStore(db)

    /** Mom (who manages the group), dad (both holding the parents key at epoch 1) and a child. */
    private fun household(): List<DeviceIdentity> {
        val family = BoardHouseholds.seed(listOf(MANAGER, PARENT, CHILD))
        family.take(2).forEach { store.putWrap(it.householdId, BoardScopes.PARENTS, 1, it.deviceId, ByteArray(60), it.deviceId) }
        return family
    }

    private fun config(family: List<DeviceIdentity>) = ServerConfig(
        version = "0.1.0",
        auth = MapAuth(family.associateBy { "tok-${it.deviceId}" }),
        devices = devices,
        boardStore = store,
        members = members,
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
    fun `a member who loses board private loses the key on every device, and hears no parents op`() = testApplication {
        val family = household()
        val (mom, dad) = family
        application { module(config(family)) }
        val http = createClient { install(WebSockets) }
        val momSocket = online(http, mom.deviceId)
        val dadSocket = online(http, dad.deviceId)

        val changed = client.put("/v1/members/${dad.memberId}/permissions") {
            header("Authorization", "Bearer tok-${mom.deviceId}")
            contentType(ContentType.Application.Json)
            setBody("""{"permissions":[]}""")
        }

        assertEquals(HttpStatusCode.OK, changed.status)
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
        assertEquals("E_SCOPE", (outcome as WrapOutcome.Refused).error.code, "no new wrap without board.private")
    }

    @Test
    fun `a change that keeps or grants board private closes nothing`() {
        val family = household()
        val (mom, _, child) = family
        val service = MemberService(members, DeviceRegistry())

        val kept = service.change(mom, mom.memberId, Permissions.FOUNDER.toList())
        val granted = service.change(mom, child.memberId, listOf(Permissions.BOARD_PRIVATE))
        val absent = service.change(mom, "00000000-0000-0000-0000-000000000000", emptyList())

        assertEquals(emptyList<String>(), (kept as PermissionOutcome.Changed).lostPrivate)
        assertEquals(emptyList<String>(), (granted as PermissionOutcome.Changed).lostPrivate)
        assertEquals("E_TENANT", (absent as PermissionOutcome.Refused).error.code)
        assertEquals(1, wrapRows(mom))
        assertEquals(0, wrapRows(child), "the permission alone hands out no key: a holder wraps it")
    }
}

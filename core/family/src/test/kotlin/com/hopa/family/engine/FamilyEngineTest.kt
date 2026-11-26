package com.hopa.family.engine

import com.hopa.family.board.BoardHash
import com.hopa.family.board.BoardJson
import com.hopa.family.board.FixtureLogs
import com.hopa.family.board.RejectCode
import com.hopa.family.model.BoardOp
import com.hopa.family.model.ItemType
import com.hopa.family.model.LogEntry
import com.hopa.family.model.NewItem
import com.hopa.family.model.Ownership
import com.hopa.family.model.Scope
import com.hopa.family.time.FamilyClock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FamilyEngineTest {
    private val seed = FixtureLogs.seed

    /** A stand-in for the server: one log, seqs in arrival order, the actor from the device. */
    private class Server {
        val log = mutableListOf<LogEntry>()

        fun append(engine: FamilyEngine, member: String) {
            for (pending in engine.outbox()) {
                if (log.any { it.op["opId"] == pending.op["opId"] }) continue
                log += LogEntry(log.size + 1L, pending.scope, member, "$member-device", pending.op)
            }
        }

        fun after(engine: FamilyEngine): List<LogEntry> = log.filter { it.seq > engine.cursor(it.scope) }
    }

    private fun engine(member: String, scopes: Set<Scope>) = FamilyEngine(
        EngineSnapshot(
            engineVersion = EngineStore.CURRENT,
            memberId = member,
            deviceId = "$member-device",
            confirmed = seed.viewOf(scopes),
            applied = emptyMap(),
            outbox = emptyList(),
        ),
    )

    private val both = setOf(Scope.FAMILY, Scope.PARENTS)
    private var n = 0

    private fun at() = FamilyClock.iso(FamilyClock.millis("2026-11-11T12:31:00.000Z") + (n++) * 60_000L)

    private fun claim(id: String, version: Int, opId: String) = BoardOp.Claim(1, opId, at(), id, version)

    private fun task(id: String, opId: String) = BoardOp.Create(1, opId, at(), NewItem(id, ItemType.TASK, "Task $id"))

    @Test
    fun `a proposal shows at once and is confirmed when the log brings it back`() {
        val mom = engine("mom", both)
        val dad = engine("dad", both)
        val server = Server()
        assertEquals(Proposed.Queued("op-milk"), mom.propose(Scope.FAMILY, task("buy-milk", "op-milk")))
        assertTrue(mom.view.scopes.family.items.any { it.id == "buy-milk" })
        assertFalse(mom.snapshot().confirmed.scopes.family.items.any { it.id == "buy-milk" })

        server.append(mom, "mom")
        val update = mom.receive(server.after(mom))
        assertEquals(listOf("op-milk"), update.confirmed)
        assertTrue(mom.outbox().isEmpty())
        dad.receive(server.after(dad))
        assertEquals(BoardHash.of(mom.view), BoardHash.of(dad.view))
        assertEquals(1L, dad.cursor(Scope.FAMILY))
    }

    @Test
    fun `two claims race, the first in the log wins and the other is dropped on both devices`() {
        val mom = engine("mom", both)
        val dad = engine("dad", both)
        val server = Server()
        mom.propose(Scope.FAMILY, claim("library-books", 1, "op-mom-claim"))
        dad.propose(Scope.FAMILY, claim("library-books", 1, "op-dad-claim"))
        server.append(mom, "mom")
        server.append(dad, "dad")

        val dadUpdate = dad.receive(server.after(dad))
        assertEquals(listOf("op-dad-claim" to RejectCode.STALE), dadUpdate.dropped)
        mom.receive(server.after(mom))
        val owner = dad.view.scopes.family.items.single { it.id == "library-books" }.ownership
        assertEquals("mom", (owner as Ownership.Assigned).ownerId)
        assertEquals(BoardHash.of(mom.view), BoardHash.of(dad.view))
    }

    @Test
    fun `a pending proposal is rebased on top of what others did first`() {
        val mom = engine("mom", both)
        val dad = engine("dad", both)
        val server = Server()
        dad.propose(Scope.FAMILY, task("fix-bike", "op-bike"))
        mom.propose(Scope.FAMILY, task("buy-milk", "op-milk"))
        server.append(mom, "mom")
        val update = dad.receive(server.after(dad))
        assertTrue(update.dropped.isEmpty())
        assertEquals(listOf("op-bike"), dad.outbox().map { it.opId })
        assertTrue(
            dad.view.scopes.family.items.any {
                it.id == "buy-milk"
            } && dad.view.scopes.family.items.any { it.id == "fix-bike" },
        )
        server.append(dad, "dad")
        dad.receive(server.after(dad))
        mom.receive(server.after(mom))
        assertEquals(BoardHash.of(mom.view), BoardHash.of(dad.view))
    }

    @Test
    fun `a stale proposal is refused before it is queued`() {
        val mom = engine("mom", both)
        assertEquals(Proposed.Rejected(RejectCode.STALE), mom.propose(Scope.FAMILY, claim("library-books", 9, "op-x")))
        assertTrue(mom.outbox().isEmpty())
    }

    @Test
    fun `a teen device ignores parents entries and cannot propose there`() {
        val maya = engine("maya", setOf(Scope.FAMILY))
        val mom = engine("mom", both)
        val server = Server()
        mom.propose(Scope.PARENTS, task("gift", "op-gift"))
        mom.propose(Scope.FAMILY, task("buy-milk", "op-milk"))
        server.append(mom, "mom")
        maya.receive(server.log)
        assertEquals(null, maya.view.scopes.parents)
        assertEquals(2L, maya.cursor(Scope.FAMILY))
        assertEquals(Proposed.ScopeNotHeld, maya.propose(Scope.PARENTS, task("x", "op-x")))
        mom.receive(server.after(mom))
        assertEquals(BoardHash.of(mom.view, Scope.FAMILY), BoardHash.of(maya.view, Scope.FAMILY))
    }

    @Test
    fun `an op this version cannot read stops the replay and holds the rest (D15)`() {
        val dad = engine("dad", both)
        val unknown =
            JsonObject(
                mapOf("v" to JsonPrimitive(1), "kind" to JsonPrimitive("archive"), "opId" to JsonPrimitive("op-new")),
            )
        val later = BoardJson.json.encodeToJsonElement(BoardOp.serializer(), task("later", "op-later")) as JsonObject
        val update = dad.receive(
            listOf(
                LogEntry(1, Scope.FAMILY, "mom", "mom-device", unknown),
                LogEntry(2, Scope.FAMILY, "mom", "mom-device", later),
            ),
        )
        assertTrue(update.updateRequired)
        assertTrue(dad.view.scopes.family.items.none { it.id == "later" })
        assertEquals(Proposed.UpdateRequired, dad.propose(Scope.FAMILY, task("x", "op-x")))
        val stored = EngineStore().decode(EngineStore().encode(dad.snapshot()))
        assertEquals(1L, stored.stoppedAt)
        assertEquals(listOf(1L, 2L), stored.held.map { it.seq })
    }

    @Test
    fun `entries already applied are skipped`() {
        val mom = engine("mom", both)
        val server = Server()
        mom.propose(Scope.FAMILY, task("buy-milk", "op-milk"))
        server.append(mom, "mom")
        mom.receive(server.log)
        val again = mom.receive(server.log)
        assertTrue(again.outcomes.isEmpty())
    }

    @Test
    fun `the engine survives a restart with its outbox`() {
        val mom = engine("mom", both)
        mom.propose(Scope.FAMILY, task("buy-milk", "op-milk"))
        val store = EngineStore()
        val restored = FamilyEngine(store.decode(store.encode(mom.snapshot())))
        assertEquals(BoardHash.of(mom.view), BoardHash.of(restored.view))
        assertEquals(listOf("op-milk"), restored.outbox().map { it.opId })
    }

    @Test
    fun `stored state is migrated forward and a newer one is refused`() {
        val text = EngineStore().encode(engine("mom", both).snapshot())
        val v2 = EngineStore(migrations = mapOf(1 to { json -> json }), current = 2)
        val migrated = v2.decode(text)
        assertEquals("mom", migrated.memberId)
        assertThrows(NewerEngineVersion::class.java) {
            EngineStore().decode(text.replace("\"engineVersion\":1", "\"engineVersion\":2"))
        }
    }

    @Test
    fun `the engine plans this member's alerts from its view`() {
        val dad = engine("dad", both)
        val due = dad.alerts(FamilyClock.millis("2026-11-11T13:31:00.000Z"), shown = emptySet())
        assertEquals(listOf("pickup-today:2026-11-11.open:a1"), due.map { it.key })
        assertTrue(due.all { it.recipientId == "dad" })
    }

    @Test
    fun `a new device starts empty with family only, and takes parents when the key arrives`() {
        val fresh = FamilyEngine.start(seed.family, "dad", "dad-device")
        assertEquals(setOf(Scope.FAMILY), fresh.scopes)
        fresh.holdParents()
        assertEquals(setOf(Scope.FAMILY, Scope.PARENTS), fresh.scopes)
        assertEquals(Proposed.Queued("op-gift"), fresh.propose(Scope.PARENTS, task("gift", "op-gift")))
    }
}

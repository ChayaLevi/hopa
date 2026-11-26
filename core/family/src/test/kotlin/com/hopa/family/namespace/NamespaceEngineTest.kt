package com.hopa.family.namespace

import com.hopa.family.board.BoardHash
import com.hopa.family.board.BoardJson
import com.hopa.family.board.FixtureLogs
import com.hopa.family.board.RejectCode
import com.hopa.family.engine.EngineStore
import com.hopa.family.engine.FamilyEngine
import com.hopa.family.engine.Proposed
import com.hopa.family.model.BoardOp
import com.hopa.family.model.ItemType
import com.hopa.family.model.LogEntry
import com.hopa.family.model.NewItem
import com.hopa.family.model.Scope
import com.hopa.family.namespace.TestNamespaces.op
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Amended D15: capabilities a device does not know, or knows in an older version, never block the rest. */
class NamespaceEngineTest {
    private val family = FixtureLogs.seed.family
    private val all = Namespaces(listOf(TestNamespaces.Notes(), TestNamespaces.Census()))
    private val notesOnly = Namespaces(listOf(TestNamespaces.Notes()))

    private fun board(id: String, opId: String, minute: Int): JsonObject = BoardJson.json.encodeToJsonElement(
        BoardOp.serializer(),
        BoardOp.Create(1, opId, "2026-11-11T12:%02d:00.000Z".format(minute), NewItem(id, ItemType.TASK, "Task $id")),
    ) as JsonObject

    private fun log(vararg ops: JsonObject): List<LogEntry> =
        ops.mapIndexed { i, op -> LogEntry(i + 1L, Scope.FAMILY, "mom", "mom-phone", op) }

    /** Board, notes and census ops interleaved: the census counts the board at its own seq. */
    private val mixed = log(
        board("a", "op-1", 31),
        op("notes", "add", "op-2", fields = arrayOf("id" to "n1", "text" to "hello")),
        op("census", "count", "op-3"),
        board("b", "op-4", 34),
        op("census", "count", "op-5"),
        op("notes", "add", "op-6", fields = arrayOf("id" to "n2", "text" to "again")),
        board("c", "op-7", 37),
    )

    private fun counts(engine: FamilyEngine) =
        engine.view.scopes.family.ns!!.getValue("census").getValue("counts").jsonArray.map { it.jsonPrimitive.int }

    @Test
    fun `a device that does not know a capability keeps it aside and the rest stays identical`() {
        val knows = FamilyEngine.start(family, "dad", "dad-laptop", all)
        val old = FamilyEngine.start(family, "dad", "dad-laptop", Namespaces.DEFAULT)
        knows.receive(mixed)
        val update = old.receive(mixed)
        assertFalse(update.updateRequired, "an unknown capability is not an update for the board")
        assertEquals(setOf("notes", "census"), old.keptAside)
        assertEquals(knows.view.scopes.family.items, old.view.scopes.family.items)
        assertEquals(knows.view.scopes.family.activity, old.view.scopes.family.activity)
        assertNull(old.view.scopes.family.ns)
        assertEquals(7L, old.cursor(Scope.FAMILY))
        assertFalse(old.needsRebuild, "this build still cannot read them")
        assertEquals(listOf(1, 2), counts(knows))
    }

    @Test
    fun `after the update a rebuild reaches exactly the state of a device that always knew`() {
        val knows = FamilyEngine.start(family, "dad", "dad-laptop", all)
        knows.receive(mixed)
        val old = FamilyEngine.start(family, "dad", "dad-laptop", Namespaces.DEFAULT)
        old.receive(mixed)

        val updated = FamilyEngine(old.snapshot(), all)
        assertTrue(updated.needsRebuild)
        val rebuilt = updated.rebuilt()
        rebuilt.receive(mixed) // the app pulls the scope again from seq 0
        assertEquals(BoardHash.of(knows.view), BoardHash.of(rebuilt.view))
        assertEquals(listOf(1, 2), counts(rebuilt), "the census counted the board as it was at each seq")
        assertFalse(rebuilt.needsRebuild)
    }

    @Test
    fun `a newer version of a capability stops only it`() {
        val engine = FamilyEngine.start(family, "dad", "dad-laptop", all)
        val update = engine.receive(
            log(
                op("notes", "add", "op-1", fields = arrayOf("id" to "n1", "text" to "x")),
                op("notes", "add", "op-2", nsv = 2, fields = arrayOf("id" to "n2", "text" to "new")),
                board("a", "op-3", 33),
                op("notes", "add", "op-4", fields = arrayOf("id" to "n3", "text" to "after")),
                op("census", "count", "op-5"),
            ),
        )
        assertTrue(update.updateRequired)
        assertEquals(setOf("notes"), engine.stopped)
        val notes = engine.view.scopes.family.ns!!.getValue("notes").getValue("notes").jsonArray
        assertEquals(
            listOf("n1"),
            notes.map { it.jsonObject["id"]!!.jsonPrimitive.content },
            "nothing of notes after the stop",
        )
        assertEquals(listOf("a"), engine.view.scopes.family.items.map { it.id }, "the board goes on")
        assertEquals(listOf(1), counts(engine), "census does not read notes, so it goes on")
        assertEquals(
            Proposed.UpdateRequired,
            engine.propose(Scope.FAMILY, op("notes", "add", "op-x", fields = arrayOf("id" to "n9"))),
        )
        assertTrue(engine.propose(Scope.FAMILY, board("z", "op-y", 50)) is Proposed.Queued)
    }

    @Test
    fun `an unknown board kind stops the board and what reads it, not the rest`() {
        val engine = FamilyEngine.start(family, "dad", "dad-laptop", all)
        val unknown =
            JsonObject(
                mapOf("v" to JsonPrimitive(1), "kind" to JsonPrimitive("archive"), "opId" to JsonPrimitive("op-1")),
            )
        engine.receive(
            log(
                unknown,
                board("a", "op-2", 32),
                op("census", "count", "op-3"),
                op("notes", "add", "op-4", fields = arrayOf("id" to "n1", "text" to "x")),
            ),
        )
        assertEquals(setOf("board", "census"), engine.stopped)
        assertTrue(engine.view.scopes.family.items.isEmpty())
        assertNull(engine.view.scopes.family.ns!!["census"])
        assertEquals(1, engine.view.scopes.family.ns!!.getValue("notes").getValue("notes").jsonArray.size)
        val stored = EngineStore().decode(EngineStore().encode(engine.snapshot()))
        assertEquals(mapOf("board" to 1L), stored.stoppedNamespaces)
        assertNull(stored.stoppedAt)
    }

    @Test
    fun `an envelope version nobody knows stops everything`() {
        val engine = FamilyEngine.start(family, "dad", "dad-laptop", all)
        val v3 =
            JsonObject(mapOf("v" to JsonPrimitive(3), "kind" to JsonPrimitive("x"), "opId" to JsonPrimitive("op-1")))
        engine.receive(log(v3, board("a", "op-2", 32), op("notes", "add", "op-3", fields = arrayOf("id" to "n1"))))
        assertTrue(engine.updateRequired)
        assertEquals(1L, engine.snapshot().stoppedAt)
        assertTrue(engine.view.scopes.family.items.isEmpty())
        assertNull(engine.view.scopes.family.ns)
        assertEquals(0L, engine.cursor(Scope.FAMILY), "the cursor stays before the stop")
    }

    @Test
    fun `malformed namespace ops are refused the same way everywhere`() {
        val engine = FamilyEngine.start(family, "dad", "dad-laptop", all)
        val update = engine.receive(
            log(
                op("board", "create", "op-1"),
                op("Bad-Name", "add", "op-2"),
                op("notes", "add", "op-3", nsv = 0),
            ),
        )
        assertEquals(List(3) { RejectCode.INVALID }, update.outcomes.map { it.code })
        val noa =
            LogEntry(4, Scope.FAMILY, "noa", "noa-tablet", op("notes", "add", "op-4", fields = arrayOf("id" to "n1")))
        assertEquals(RejectCode.FORBIDDEN, engine.receive(listOf(noa)).outcomes.single().code)
    }

    @Test
    fun `a capability proposal shows at once and is rebased like a board one`() {
        val engine = FamilyEngine.start(family, "mom", "mom-phone", all)
        val add = op("notes", "add", "op-mine", fields = arrayOf("id" to "n1", "text" to "milk"))
        assertEquals(Proposed.Queued("op-mine"), engine.propose(Scope.FAMILY, add))
        val other =
            LogEntry(
                1,
                Scope.FAMILY,
                "dad",
                "dad-laptop",
                op("notes", "add", "op-dad", fields = arrayOf("id" to "n1", "text" to "bread")),
            )
        val update = engine.receive(listOf(other))
        assertEquals(listOf("op-mine" to RejectCode.CONFLICT), update.dropped, "same note id: first writer wins")
    }

    @Test
    fun `stored v1 engine state migrates to v2`() {
        val engine = FamilyEngine.start(family, "mom", "mom-phone", all)
        engine.receive(log(board("a", "op-1", 31)))
        val v2 = EngineStore().encode(engine.snapshot())
        val v1 = v2
            .replace("\"engineVersion\":2", "\"engineVersion\":1,\"stoppedAt\":5")
            .replace("\"schemaVersion\":2", "\"schemaVersion\":1")
        val migrated = EngineStore().decode(v1)
        assertEquals(2, migrated.engineVersion)
        assertEquals(2, migrated.confirmed.schemaVersion)
        assertNull(migrated.stoppedAt)
        assertEquals(1L, migrated.applied[Scope.FAMILY])
        assertEquals(listOf("a"), FamilyEngine(migrated, all).view.scopes.family.items.map { it.id })
    }

    @Test
    fun `the registry checks names and reads, and knows who reads whom`() {
        assertThrows(IllegalArgumentException::class.java) {
            Namespaces(listOf(TestNamespaces.Census(), TestNamespaces.Census()))
        }
        val readsNotes = object : Namespace by TestNamespaces.Census() {
            override val name = "digest"
            override val reads = setOf("notes")
        }
        assertThrows(IllegalArgumentException::class.java) { Namespaces(listOf(readsNotes)) }
        val registry = Namespaces(listOf(TestNamespaces.Notes(), TestNamespaces.Census(), readsNotes))
        assertEquals(setOf("census"), registry.readersOf("board"))
        assertEquals(setOf("digest"), registry.readersOf("notes"))
        assertTrue(registry.knows("board") && !registry.knows("shopping"))
        assertTrue(notesOnly.readersOf("board").isEmpty())
    }
}

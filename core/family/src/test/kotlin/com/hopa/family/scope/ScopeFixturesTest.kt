package com.hopa.family.scope

import com.hopa.contracts.json.CanonicalJson
import com.hopa.family.board.BoardJson
import com.hopa.family.board.BoardReplay
import com.hopa.family.board.FixtureLogs
import com.hopa.family.model.BoardOp
import com.hopa.family.model.BoardState
import com.hopa.family.model.LogEntry
import com.hopa.family.model.Scope
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * The scope helpers write exactly the ops of the generated fixtures (which Homemind's core applied),
 * so a move or a derivation made on a device is one the fixtures already prove on every client.
 */
class ScopeFixturesTest {
    private val both = setOf(Scope.FAMILY, Scope.PARENTS)

    @Test
    fun `moving an open task to parents writes the fixture ops`() {
        val log = log("board-visibility.json", "move-open-task-to-parents-narrows-candidates")
        assertMove(log, before = 0, by = "dad", itemId = "library-books", to = Scope.PARENTS)
    }

    @Test
    fun `moving a task a teen owns writes the fixture ops`() {
        val log = log("board-visibility.json", "move-task-owned-by-teen-opens-to-parents")
        assertMove(log, before = 1, by = "mom", itemId = "library-books", to = Scope.PARENTS)
    }

    @Test
    fun `moving a weekly event writes the fixture ops`() {
        val log = log("board-visibility.json", "move-recurring-event-keeps-exceptions")
        assertMove(log, before = 2, by = "mom", itemId = "swim-weekly", to = Scope.PARENTS)
    }

    @Test
    fun `undoing a move writes the fixture ops`() {
        val log = log("board-visibility.json", "undo-a-move-is-two-undos")
        val moved = assertMove(log, before = 0, by = "mom", itemId = "pickup-tuesday", to = Scope.PARENTS)
        val undo = VisibilityMove.undo(moved, stampsFrom(log.drop(2)))
        assertEquals(canonical(log.drop(2)), canonical(undo))
    }

    @Test
    fun `a busy event derived from a parents event writes the fixture op`() {
        val log = log("board-scopes.json", "derive-busy-event-for-family")
        val state = replay(log.take(1))
        val entry = log[1]
        val derived = BusyDerivation.derive(
            state,
            actorId = "mom",
            eventId = "mom-doctor",
            newId = "mom-busy",
            stamp = stampOf(entry),
        )
        assertEquals(canonical(listOf(entry)), canonical(listOfNotNull(derived)))
    }

    /** Replays the first [before] entries, plans the move, and checks it against the next entries. */
    private fun assertMove(log: List<LogEntry>, before: Int, by: String, itemId: String, to: Scope): List<ScopedOp> {
        val state = replay(log.take(before))
        val from = if (to == Scope.PARENTS) Scope.FAMILY else Scope.PARENTS
        val expected = log.drop(before).take(2)
        val newId = expected[1].op.getValue("item").jsonObject.getValue("id").jsonPrimitive.content
        val plan = VisibilityMove.plan(state, by, itemId, from, to, { newId }, stampsFrom(expected))
        val ops = (plan as MovePlan.Ops).ops
        assertEquals(canonical(expected), canonical(ops))
        return ops
    }

    private fun replay(entries: List<LogEntry>): BoardState {
        val result = BoardReplay.replay(FixtureLogs.seed, entries, both)
        assertEquals(entries.map { "applied" }, result.outcomes.map { it.outcome })
        return result.state
    }

    private fun stampOf(entry: LogEntry) = OpStamp(
        opId = entry.op.getValue("opId").jsonPrimitive.content,
        at = entry.op.getValue("at").jsonPrimitive.content,
    )

    private fun stampsFrom(entries: List<LogEntry>): OpStamps {
        val stamps = entries.map(::stampOf).iterator()
        return OpStamps { stamps.next() }
    }

    private fun canonical(entries: List<LogEntry>): List<String> =
        entries.map { "${it.scope} " + CanonicalJson.encode(it.op) }

    @JvmName("canonicalOps")
    private fun canonical(ops: List<ScopedOp>): List<String> = ops.map {
        "${it.scope} " + CanonicalJson.encode(BoardJson.json.encodeToJsonElement(BoardOp.serializer(), it.op))
    }

    private fun log(file: String, case: String): List<LogEntry> {
        val dir = File(requireNotNull(javaClass.getResource("/fixtures/family")).toURI())
        val found = BoardJson.json.parseToJsonElement(File(dir, file).readText()).jsonObject
            .getValue("cases").jsonArray.map { it.jsonObject }
            .single { it.getValue("name").jsonPrimitive.content == case }
        return BoardJson.json.decodeFromJsonElement(ListSerializer(LogEntry.serializer()), found.getValue("log"))
    }
}

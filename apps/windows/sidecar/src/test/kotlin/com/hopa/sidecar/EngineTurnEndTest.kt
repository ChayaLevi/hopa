package com.hopa.sidecar

import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * The engine's end hook is the gateway's only way out of a turn, so it must
 * fire once even when the turn fails before `turn.end` (R31, Important 2).
 * [Engine.guardedTurn] is the failure seam; no model or host is started.
 */
class EngineTurnEndTest {
    @Test
    fun `a turn that throws before its end still fires the hook exactly once`() = runBlocking {
        val engine = engine()
        val ends = mutableListOf<Pair<String, String>>()
        engine.onTurnEnd = { id, answer -> ends += id to answer }

        engine.guardedTurn("turn-1") { throw IllegalStateException("store exploded with goal text") }

        assertEquals(1, ends.size)
        assertEquals("turn-1", ends.single().first)
        assertFalse(ends.single().second.contains("store exploded"))
    }

    @Test
    fun `a successful turn fires the hook once with its answer`() = runBlocking {
        val engine = engine()
        val ends = mutableListOf<Pair<String, String>>()
        engine.onTurnEnd = { id, answer -> ends += id to answer }

        engine.guardedTurn("turn-2") { "opened settings" }

        assertEquals(listOf("turn-2" to "opened settings"), ends)
    }

    private fun engine(): Engine {
        val directory: Path = Files.createTempDirectory("hopa-engine-turn-end")
        val io = Stdio()
        return Engine(
            io = io,
            hostDevice = HostDevice(io),
            approval = HostApproval(io, PendingApprovals(directory.resolve("approvals.json"))),
            store = ChatStore(directory.resolve("conversations.json")),
            recordStore = RecordStore(directory.resolve("records.json")),
            config = RuntimeConfig(),
        )
    }
}

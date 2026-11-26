package com.hopa.family.board

import com.hopa.family.model.BoardState
import com.hopa.family.model.LogEntry
import com.hopa.family.model.Scope

/**
 * A simulated device for determinism tests. It holds [held] scopes, is sent only the entries of
 * those scopes (as the server does), and keeps its state only as stored JSON between deliveries, as
 * an app that is closed and opened again would. After an op it cannot read it stops and keeps
 * every later entry queued, never skipping one (D15).
 */
class DeviceEngine(seed: BoardState, private val held: Set<Scope>) {
    private var stored: String = BoardJson.encodeState(seed.viewOf(held))
    private val queued = mutableListOf<LogEntry>()
    private val reached = mutableListOf<Outcome>()

    val outcomes: List<Outcome> get() = reached.toList()
    val queue: List<LogEntry> get() = queued.toList()
    var stoppedAt: Long? = null
        private set

    val state: BoardState get() = BoardJson.decodeState(stored)

    /** One delivery from the server: the entries of [batch] this device may read, in seq order. */
    fun receive(batch: List<LogEntry>) {
        val wire = batch.filter { it.scope in held }.map(::overTheWire)
        if (stoppedAt != null) {
            queued += wire
            return
        }
        val result = BoardReplay.replay(BoardJson.decodeState(stored), wire, held)
        stored = BoardJson.encodeState(result.state)
        reached += result.outcomes
        stoppedAt = result.stoppedAt
        result.stoppedAt?.let { stop -> queued += wire.filter { it.seq >= stop } }
    }

    fun hash(): String = BoardHash.of(state)

    fun hash(scope: Scope): String = BoardHash.of(state, scope)

    /** An entry decoded afresh from its JSON text, so no object is shared with another engine. */
    private fun overTheWire(entry: LogEntry): LogEntry = BoardJson.json.decodeFromString(
        LogEntry.serializer(),
        BoardJson.json.encodeToString(LogEntry.serializer(), entry),
    )
}

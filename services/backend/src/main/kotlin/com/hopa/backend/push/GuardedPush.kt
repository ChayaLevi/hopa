package com.hopa.backend.push

import com.hopa.backend.http.RateLimiter
import java.util.concurrent.ConcurrentHashMap

/**
 * Six wakes a minute per device, and one wake per task in the dedupe window.
 * Both are process-local: v0 is one container, and the durable record is the
 * task row, not the push.
 *
 * Board wakes are a separate budget ([boardWakesPerMinute] per device), so a
 * burst of board ops can never use up the wakes a task needs. They are also
 * coalesced per device: inside [boardCoalesceMs] of the last board wake, a new
 * one is held back and replaced by any later seq, and [flushBoardWakes] sends
 * the latest once the window has passed. A device that wakes pulls every op
 * after its cursor, so one wake with the latest seq is enough.
 */
class GuardedPush(
    private val sender: PushSender,
    wakesPerMinute: Int = 6,
    private val clock: () -> Long = System::currentTimeMillis,
    boardWakesPerMinute: Int = 6,
    private val boardCoalesceMs: Long = 10_000,
) : PushPort {
    private val limiter = RateLimiter(wakesPerMinute, 60_000, clock)
    private val woken = ConcurrentHashMap<String, Long>()

    private class BoardWake(val deviceId: String, val scope: String, val seq: Long, val pushToken: String)

    private val boardLimiter = RateLimiter(boardWakesPerMinute, 60_000, clock)
    private val boardLock = Any()
    private val boardSentAt = HashMap<String, Long>()
    private val boardSentSeq = HashMap<String, Long>()
    private val boardPending = LinkedHashMap<String, BoardWake>()

    override fun wake(deviceId: String, taskId: String, traceId: String, pushToken: String): PushOutcome {
        val now = clock()
        woken.entries.removeIf { it.value <= now - 60_000 }
        if (woken.containsKey(taskId)) return PushOutcome.Skipped
        if (!limiter.allow(deviceId)) return PushOutcome.Skipped

        woken[taskId] = now
        return sender.send(pushToken, mapOf("taskId" to taskId, "traceId" to traceId))
    }

    override fun wakeBoard(deviceId: String, scope: String, seq: Long, pushToken: String): PushOutcome {
        val due = synchronized(boardLock) {
            // A wake for a seq the device was already woken for says nothing new.
            if (seq <= (boardSentSeq[deviceId] ?: 0L)) return PushOutcome.Skipped
            val held = boardPending[deviceId]
            val latest = if (held != null && held.seq > seq) held else BoardWake(deviceId, scope, seq, pushToken)
            if (!readyForBoardWake(deviceId)) {
                boardPending[deviceId] = latest
                return PushOutcome.Skipped
            }
            boardPending.remove(deviceId)
            markBoardSent(latest)
            latest
        }
        return sendBoard(due)
    }

    override fun flushBoardWakes(): Int {
        val due = synchronized(boardLock) {
            val ready = boardPending.values.filter { readyForBoardWake(it.deviceId) }
            ready.forEach {
                boardPending.remove(it.deviceId)
                markBoardSent(it)
            }
            ready
        }
        due.forEach(::sendBoard)
        return due.size
    }

    /** Outside the device's coalescing window and inside its board budget; spends one wake when true. */
    private fun readyForBoardWake(deviceId: String): Boolean {
        val last = boardSentAt[deviceId]
        if (last != null && clock() - last < boardCoalesceMs) return false
        return boardLimiter.allow(deviceId)
    }

    private fun markBoardSent(wake: BoardWake) {
        boardSentAt[wake.deviceId] = clock()
        boardSentSeq[wake.deviceId] = wake.seq
    }

    private fun sendBoard(wake: BoardWake): PushOutcome =
        sender.send(
            wake.pushToken,
            mapOf("type" to "board.changed", "scope" to wake.scope, "seq" to wake.seq.toString()),
        )
}

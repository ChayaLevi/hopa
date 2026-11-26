package com.hopa.family.board

import com.hopa.family.model.BoardState
import com.hopa.family.model.LogEntry
import com.hopa.family.model.Scope

/**
 * Replays a log in seq order on a device that holds [held] scopes. Entries of other scopes never
 * reach it (the server does not send them). Replay stops at an op this reader does not know (D15)
 * and keeps the state it had reached.
 */
object BoardReplay {
    fun replay(start: BoardState, log: List<LogEntry>, held: Set<Scope>): ReplayResult {
        var state = start.viewOf(held)
        val outcomes = mutableListOf<Outcome>()
        var last = 0L
        for (entry in log) {
            require(entry.seq > last) { "seq ${entry.seq} is not after $last" }
            last = entry.seq
            if (entry.scope !in held) continue
            when (val step = BoardReducer.apply(state, entry)) {
                is BoardStep.Applied -> {
                    state = step.state
                    outcomes += Outcome.applied(entry.seq)
                }
                is BoardStep.Rejected -> outcomes += Outcome.rejected(entry.seq, step.code)
                BoardStep.Stopped -> return ReplayResult(state, outcomes, stoppedAt = entry.seq)
            }
        }
        return ReplayResult(state, outcomes)
    }
}

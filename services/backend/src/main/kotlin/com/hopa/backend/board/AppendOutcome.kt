package com.hopa.backend.board

/** What an append did. A store never throws for a reused op id. */
sealed interface AppendOutcome {
    /** Stored at [seq]; [duplicate] when the same op was stored before and nothing was written. */
    data class Stored(val seq: Long, val duplicate: Boolean) : AppendOutcome

    /** The op id is already stored with another body or scope. */
    data object Conflict : AppendOutcome
}

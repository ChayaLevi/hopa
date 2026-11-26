package com.hopa.family.engine

import com.hopa.family.board.Outcome
import com.hopa.family.board.RejectCode

/** What one batch of log entries changed on this device. */
data class EngineUpdate(
    /** One per entry applied or refused, in seq order. */
    val outcomes: List<Outcome>,
    /** Own proposals the log now holds, applied. */
    val confirmed: List<String>,
    /**
     * Own proposals that lost: refused when the log reached them, or no longer valid on top of what
     * others did first (first writer wins, D8). Each with the reducer's code, for the screen.
     */
    val dropped: List<Pair<String, RejectCode>>,
    /** Set when an op this version cannot read stopped the replay (D15). */
    val updateRequired: Boolean,
)

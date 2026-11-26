package com.hopa.backend.board

/**
 * Where a scope's key stands in a household. [currentEpoch] is the highest
 * epoch with any wrap, or null when no wrap exists. [lastEpoch] is the highest
 * epoch the household has ever used, from the wraps and from the ops, so a
 * key whose wraps are all gone is still remembered by the ops written under it.
 */
data class ScopeKeyState(val currentEpoch: Int?, val lastEpoch: Int) {
    /** The epoch a bootstrap creates now; null while a current key exists. */
    val bootstrapEpoch: Int? get() = if (currentEpoch == null) lastEpoch + 1 else null

    /** The epoch whose ops a bootstrap leaves unreadable; null when nothing is lost. */
    val lostEpoch: Int? get() = if (currentEpoch == null && lastEpoch > 0) lastEpoch else null
}

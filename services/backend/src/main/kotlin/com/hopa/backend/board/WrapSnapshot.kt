package com.hopa.backend.board

/**
 * What the wrap rules need to know, read in the same transaction (and under
 * the same lock) as the write that follows. [callerPrivate] and [targetPrivate]
 * say whether the device's member holds `board.private`; [targetExists] is
 * false for a device that is not in the household or is revoked.
 */
class WrapSnapshot(
    val callerPrivate: Boolean,
    val targetExists: Boolean,
    val targetPrivate: Boolean,
    val state: ScopeKeyState,
    val callerHolds: Boolean,
)

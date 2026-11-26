package com.hopa.backend.board

/**
 * What the wrap rules need to know, read in the same transaction (and under
 * the same lock) as the write that follows. A role is null when the device's
 * member has no family role; [targetExists] is false for a device that is not
 * in the household or is revoked.
 */
class WrapSnapshot(
    val callerRole: String?,
    val targetExists: Boolean,
    val targetRole: String?,
    val state: ScopeKeyState,
    val callerHolds: Boolean,
)

package com.hopa.backend.board

import com.hopa.backend.protocol.GatewayError

/**
 * Who may store a scope-key wrap for whom (ADR-0033, bootstrap and holder
 * rules). Pure, so every store applies the same answer to the snapshot it read
 * under its lock.
 *
 * - The key goes only to a device whose member holds `board.private` (shown to
 *   users as a parent), and only such a device hands it out.
 * - With no wrap in the household, such a device bootstraps the key for itself
 *   at the next epoch (one above any epoch used before).
 * - Otherwise only a device holding the current epoch may wrap, at that epoch.
 */
object ScopeKeyRules {
    fun decide(request: WrapRequest, snapshot: WrapSnapshot): WrapOutcome {
        if (!snapshot.targetExists) return refused("E_TENANT", "no such device in this household")
        if (!snapshot.callerPrivate) return refused("E_SCOPE", "only a device of a member with board.private may hand out the parents key")
        if (!snapshot.targetPrivate) return refused("E_SCOPE", "the parents key goes only to a device of a member with board.private")
        val state = snapshot.state
        val current = state.currentEpoch
        if (current == null) {
            if (request.targetDeviceId != request.callerDeviceId) {
                return refused("E_SCOPE", "the first key is created by a device for itself")
            }
            if (request.keyEpoch != state.bootstrapEpoch) {
                return refused("E_CONFLICT", "a new key starts at epoch ${state.bootstrapEpoch}")
            }
            return WrapOutcome.Stored(request.keyEpoch, bootstrap = true, lostEpoch = state.lostEpoch)
        }
        if (!snapshot.callerHolds) {
            return if (request.targetDeviceId == request.callerDeviceId) {
                refused("E_CONFLICT", "the key already exists at epoch $current; ask a holder to share it")
            } else {
                refused("E_SCOPE", "this device holds no current key for scope ${request.scope}")
            }
        }
        if (request.keyEpoch != current) return refused("E_CONFLICT", "the current epoch is $current")
        return WrapOutcome.Stored(current, bootstrap = false, lostEpoch = null)
    }

    private fun refused(code: String, message: String) = WrapOutcome.Refused(GatewayError(code, message))
}

package com.hopa.backend.board

import com.hopa.backend.protocol.GatewayError

/**
 * Who may store a scope-key wrap for whom (ADR-0033, bootstrap and holder
 * rules). Pure, so every store applies the same answer to the snapshot it read
 * under its lock.
 *
 * - The key goes only to a parent's device, and only a parent's device hands it out.
 * - With no wrap in the household, a parent device bootstraps the key for itself
 *   at the next epoch (one above any epoch used before).
 * - Otherwise only a device holding the current epoch may wrap, at that epoch.
 */
object ScopeKeyRules {
    const val PARENT = "parent"

    fun decide(request: WrapRequest, snapshot: WrapSnapshot): WrapOutcome {
        if (!snapshot.targetExists) return refused("E_TENANT", "no such device in this household")
        if (snapshot.callerRole != PARENT) return refused("E_SCOPE", "only a parent's device may hand out the parents key")
        if (snapshot.targetRole != PARENT) return refused("E_SCOPE", "the parents key goes only to a parent's device")
        val state = snapshot.state
        val current = state.currentEpoch
        if (current == null) {
            if (request.targetDeviceId != request.callerDeviceId) {
                return refused("E_SCOPE", "the first key is created by a parent's device for itself")
            }
            if (request.keyEpoch != state.bootstrapEpoch) {
                return refused("E_CONFLICT", "a new key starts at epoch ${state.bootstrapEpoch}")
            }
            return WrapOutcome.Stored(request.keyEpoch, bootstrap = true, lostEpoch = state.lostEpoch)
        }
        if (!snapshot.callerHolds) {
            return if (request.targetDeviceId == request.callerDeviceId) {
                refused("E_CONFLICT", "the key already exists at epoch $current; ask a parent to share it")
            } else {
                refused("E_SCOPE", "this device holds no current key for scope ${request.scope}")
            }
        }
        if (request.keyEpoch != current) return refused("E_CONFLICT", "the current epoch is $current")
        return WrapOutcome.Stored(current, bootstrap = false, lostEpoch = null)
    }

    private fun refused(code: String, message: String) = WrapOutcome.Refused(GatewayError(code, message))
}

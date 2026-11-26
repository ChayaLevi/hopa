package com.hopa.backend.board

import com.hopa.backend.gateway.DeviceRegistry

/**
 * Changes a member's family role and its consequences for the board (ADR-0033):
 * a member who stops being a parent loses the `parents` wraps of all their
 * devices in one transaction, then their live sockets are closed so the client
 * starts again from what it may read now. From then on the server gives those
 * devices no `parents` op and no new wrap.
 *
 * There is no members or roles write API in this work (O2): the test harness
 * calls this, and lane B's role changes must call it when they land.
 */
class FamilyRoles(
    private val store: BoardStore,
    private val registry: DeviceRegistry,
) {
    /** Sets [memberId]'s family role; returns the devices that lost the parents scope. */
    fun setFamilyRole(householdId: String, memberId: String, role: String?): List<String> {
        val demoted = store.setFamilyRole(householdId, memberId, role)
        // After the commit: a socket closed before it could reconnect to the
        // same rights it just lost.
        demoted.forEach(registry::disconnect)
        return demoted
    }
}

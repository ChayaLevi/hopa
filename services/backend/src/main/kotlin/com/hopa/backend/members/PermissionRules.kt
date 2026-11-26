package com.hopa.backend.members

import com.hopa.backend.protocol.GatewayError

/**
 * Who may change whose permissions. Pure, so every store applies the same
 * answer to the snapshot it read under its lock.
 *
 * - Only a member holding [Permissions.GROUP_MANAGE] changes permissions (also their own).
 * - The member must be in the caller's group.
 * - A group never loses its last manager.
 */
object PermissionRules {
    /** Null when the change is allowed; the refusal otherwise. */
    fun decide(change: PermissionChange, snapshot: PermissionSnapshot): GatewayError? {
        if (Permissions.GROUP_MANAGE !in snapshot.caller) {
            return GatewayError("E_SCOPE", "only a member who manages the group may change permissions")
        }
        val current = snapshot.target ?: return GatewayError("E_TENANT", "no such member in this group")
        val dropsManager = Permissions.GROUP_MANAGE in current && Permissions.GROUP_MANAGE !in change.permissions
        if (dropsManager && snapshot.managers <= 1) {
            return GatewayError("E_CONFLICT", "the group must keep at least one manager")
        }
        return null
    }
}

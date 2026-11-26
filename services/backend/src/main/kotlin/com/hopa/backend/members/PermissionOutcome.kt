package com.hopa.backend.members

import com.hopa.backend.protocol.GatewayError

/** The result of a permission change. */
sealed interface PermissionOutcome {
    /**
     * Stored. [lostPrivate] lists the member's devices when the change took
     * [Permissions.BOARD_PRIVATE] away: their `parents` wraps are already gone,
     * and their sockets should be closed.
     */
    class Changed(val member: GroupMember, val lostPrivate: List<String>) : PermissionOutcome

    class Refused(val error: GatewayError) : PermissionOutcome
}

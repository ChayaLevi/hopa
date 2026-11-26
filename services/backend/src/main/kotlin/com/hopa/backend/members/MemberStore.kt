package com.hopa.backend.members

import com.hopa.backend.protocol.GatewayError

/** The members of a group and their permissions. Every call is scoped to one household. */
interface MemberStore {
    /** Every member of the household, by display name then id. */
    fun list(householdId: String): List<GroupMember>

    /**
     * Applies [change] when [PermissionRules] allow it. The rules are checked on a
     * snapshot read in the same transaction as the write, serialized per
     * household. When the member loses [Permissions.BOARD_PRIVATE], the `parents`
     * wraps of all their devices go in the same transaction.
     */
    fun change(householdId: String, change: PermissionChange): PermissionOutcome

    /** No member directory: a server without a database. */
    object Empty : MemberStore {
        override fun list(householdId: String): List<GroupMember> = emptyList()

        override fun change(householdId: String, change: PermissionChange): PermissionOutcome =
            PermissionOutcome.Refused(GatewayError("E_TENANT", "no such member in this group"))
    }
}

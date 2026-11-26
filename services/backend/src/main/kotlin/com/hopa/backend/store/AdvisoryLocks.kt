package com.hopa.backend.store

import java.sql.Connection

/**
 * Transaction-scoped advisory locks, held until commit. The keys live here so
 * every writer of the same rows takes the same lock. When a transaction takes
 * both, it takes [members] first.
 */
internal object AdvisoryLocks {
    /** Serializes permission changes of one household (V7). */
    fun members(connection: Connection, householdId: String) = take(connection, "hopa.member_permissions:$householdId")

    /** Serializes scope-key wrap writes, and the drops a permission change makes, per household and scope (V6). */
    fun scopeKeyWraps(connection: Connection, householdId: String, scope: String) =
        take(connection, "hopa.scope_key_wraps:$householdId:$scope")

    private fun take(connection: Connection, key: String) {
        connection.prepareStatement("select pg_advisory_xact_lock(hashtextextended(?, 0))").use {
            it.setString(1, key)
            it.executeQuery().close()
        }
    }
}

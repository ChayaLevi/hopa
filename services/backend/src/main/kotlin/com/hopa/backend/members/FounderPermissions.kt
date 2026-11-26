package com.hopa.backend.members

import java.sql.Connection

/**
 * Gives the member who creates a household every permission
 * ([Permissions.FOUNDER]) inside the transaction that creates it, so a new
 * group always has a manager and a holder of the private scope.
 */
object FounderPermissions {
    fun grant(connection: Connection, householdId: String, memberId: String) {
        connection.prepareStatement(
            "insert into member_permissions(household_id, member_id, permission) values (?::uuid, ?::uuid, ?)",
        ).use { statement ->
            for (permission in Permissions.FOUNDER.sorted()) {
                statement.setString(1, householdId)
                statement.setString(2, memberId)
                statement.setString(3, permission)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }
}

package com.hopa.backend.store

import com.hopa.backend.board.BoardScopes
import com.hopa.backend.members.GroupMember
import com.hopa.backend.members.MemberStore
import com.hopa.backend.members.PermissionChange
import com.hopa.backend.members.PermissionOutcome
import com.hopa.backend.members.PermissionRules
import com.hopa.backend.members.PermissionSnapshot
import com.hopa.backend.members.Permissions
import java.sql.Connection

/**
 * Members and their permissions in Postgres (V7). Every statement runs in a
 * household transaction, so the policies decide what this store can see: a
 * member of another household reads as absent.
 *
 * A change takes the household's member lock, then the `parents` wrap lock
 * that [PgBoardStore.writeWrap] takes. So two managers cannot both step down,
 * and a wrap for a member losing [Permissions.BOARD_PRIVATE] is either written
 * before the drop (and dropped with it) or refused after it.
 */
class PgMemberStore(private val db: Database) : MemberStore {
    override fun list(householdId: String): List<GroupMember> = db.tenant(householdId) { connection ->
        connection.prepareStatement(
            """
            select m.id, m.display_name, m.account_id is not null,
                   coalesce(string_agg(p.permission, ',' order by p.permission), '')
            from members m left join member_permissions p on p.member_id = m.id
            group by m.id, m.display_name, m.account_id
            order by m.display_name, m.id
            """.trimIndent(),
        ).use {
            it.executeQuery().use { rows ->
                buildList {
                    while (rows.next()) {
                        add(GroupMember(rows.getString(1), rows.getString(2), split(rows.getString(4)), rows.getBoolean(3)))
                    }
                }
            }
        }
    }

    override fun change(householdId: String, change: PermissionChange): PermissionOutcome = db.tenant(householdId) { connection ->
        AdvisoryLocks.members(connection, householdId)
        AdvisoryLocks.scopeKeyWraps(connection, householdId, BoardScopes.PARENTS)
        val target = permissionsOf(connection, change.memberId)
        val snapshot = PermissionSnapshot(
            caller = permissionsOf(connection, change.callerMemberId).orEmpty(),
            target = target,
            managers = managers(connection),
        )
        PermissionRules.decide(change, snapshot)?.let { return@tenant PermissionOutcome.Refused(it) }
        replace(connection, householdId, change.memberId, change.permissions)
        val lostPrivate = if (Permissions.BOARD_PRIVATE in target!! && Permissions.BOARD_PRIVATE !in change.permissions) {
            dropParentsWraps(connection, householdId, change.memberId)
        } else {
            emptyList()
        }
        PermissionOutcome.Changed(member(connection, change.memberId), lostPrivate)
    }

    /** The member's permissions; null when the member is not in this household. */
    private fun permissionsOf(connection: Connection, memberId: String): Set<String>? =
        connection.prepareStatement(
            """
            select p.permission
            from members m left join member_permissions p on p.member_id = m.id
            where m.id = ?::uuid
            """.trimIndent(),
        ).use {
            it.setString(1, memberId)
            it.executeQuery().use { rows ->
                var found = false
                val permissions = buildSet {
                    while (rows.next()) {
                        found = true
                        rows.getString(1)?.let(::add)
                    }
                }
                permissions.takeIf { found }
            }
        }

    private fun managers(connection: Connection): Int =
        connection.prepareStatement("select count(*) from member_permissions where permission = ?").use {
            it.setString(1, Permissions.GROUP_MANAGE)
            it.executeQuery().use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }

    private fun replace(connection: Connection, householdId: String, memberId: String, permissions: Set<String>) {
        connection.prepareStatement("delete from member_permissions where member_id = ?::uuid").use {
            it.setString(1, memberId)
            it.executeUpdate()
        }
        connection.prepareStatement(
            "insert into member_permissions(household_id, member_id, permission) values (?::uuid, ?::uuid, ?)",
        ).use { statement ->
            for (permission in permissions.sorted()) {
                statement.setString(1, householdId)
                statement.setString(2, memberId)
                statement.setString(3, permission)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    /** Drops the `parents` wraps of all the member's devices; returns those devices. */
    private fun dropParentsWraps(connection: Connection, householdId: String, memberId: String): List<String> {
        connection.prepareStatement(
            """
            delete from scope_key_wraps
            where household_id = ?::uuid and scope = ?
              and device_id in (select id from devices where member_id = ?::uuid)
            """.trimIndent(),
        ).use {
            it.setString(1, householdId)
            it.setString(2, BoardScopes.PARENTS)
            it.setString(3, memberId)
            it.executeUpdate()
        }
        return connection.prepareStatement("select id from devices where member_id = ?::uuid order by id").use {
            it.setString(1, memberId)
            it.executeQuery().use { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }
        }
    }

    private fun member(connection: Connection, memberId: String): GroupMember =
        connection.prepareStatement(
            """
            select m.id, m.display_name, m.account_id is not null,
                   coalesce(string_agg(p.permission, ',' order by p.permission), '')
            from members m left join member_permissions p on p.member_id = m.id
            where m.id = ?::uuid
            group by m.id, m.display_name, m.account_id
            """.trimIndent(),
        ).use {
            it.setString(1, memberId)
            it.executeQuery().use { rows ->
                rows.next()
                GroupMember(rows.getString(1), rows.getString(2), split(rows.getString(4)), rows.getBoolean(3))
            }
        }

    private fun split(joined: String): List<String> = if (joined.isEmpty()) emptyList() else joined.split(',')
}

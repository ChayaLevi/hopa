package com.hopa.backend.store

import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.harness.newDevice
import com.hopa.backend.harness.newHousehold
import com.hopa.backend.harness.seedHousehold
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Schema v6: the family board tables (ADR-0031). Every new table is household
 * scoped with RLS enabled and forced, so a connection with no tenant setting
 * sees nothing, as on every V1 table.
 */
class V6SchemaTest {
    private val db = PostgresHarness.appDatabase()

    private val boardTables = listOf("board_cursors", "board_heads", "board_ops", "scope_key_wraps")

    @Test
    fun `v6 creates the board tables and re-applies cleanly`() {
        val owner = PostgresHarness.ownerDatabase()

        assertEquals(0, owner.migrate())
        assertEquals(0, owner.migrate())

        val present = PostgresHarness.adminDatabase().dataSource.connection.use { connection ->
            connection.strings(
                "select table_name from information_schema.tables " +
                    "where table_schema = 'public' and table_name in " +
                    "('board_heads', 'board_ops', 'board_cursors', 'scope_key_wraps') " +
                    "order by table_name",
            )
        }

        assertEquals(boardTables, present)
    }

    @Test
    fun `every board table has row level security enabled and forced`() {
        val flags = PostgresHarness.adminDatabase().dataSource.connection.use { connection ->
            connection.strings(
                "select relname || ':' || relrowsecurity || ':' || relforcerowsecurity " +
                    "from pg_class where relname in " +
                    "('board_heads', 'board_ops', 'board_cursors', 'scope_key_wraps') " +
                    "order by relname",
            )
        }

        assertEquals(boardTables.map { "$it:true:true" }, flags)
    }

    @Test
    fun `board rows are invisible without a tenant`() {
        val home = newHousehold()
        val other = newHousehold()
        val device = newDevice()
        val member = UUID.randomUUID().toString()
        seedHousehold(db, home, device, member)
        seedHousehold(db, other, newDevice(), UUID.randomUUID().toString())
        db.tenant(home) { connection -> seedBoardRows(connection, home, device, member) }

        val withoutTenant = db.dataSource.connection.use { connection ->
            boardTables.map { connection.count("select count(*) from $it") }
        }
        val ownTenant = db.tenant(home) { connection ->
            boardTables.map { connection.count("select count(*) from $it") }
        }
        val otherTenant = db.tenant(other) { connection ->
            boardTables.map { connection.count("select count(*) from $it") }
        }

        assertEquals(listOf(0, 0, 0, 0), withoutTenant)
        assertEquals(listOf(1, 1, 1, 1), ownTenant)
        assertEquals(listOf(0, 0, 0, 0), otherTenant)
    }

    @Test
    fun `the owner role is forced through the board policies`() {
        val owner = PostgresHarness.ownerDatabase()
        val household = newHousehold()
        val device = newDevice()
        val member = UUID.randomUUID().toString()
        seedHousehold(owner, household, device, member)
        owner.tenant(household) { connection -> seedBoardRows(connection, household, device, member) }

        val visible = owner.dataSource.connection.use { connection ->
            boardTables.map { connection.count("select count(*) from $it") }
        }

        assertEquals(listOf(0, 0, 0, 0), visible)
    }

    @Test
    fun `scope is limited to family and parents`() {
        val household = newHousehold()
        val device = newDevice()
        val member = UUID.randomUUID().toString()
        seedHousehold(db, household, device, member)

        assertThrows<SQLException> {
            db.tenant(household) { connection ->
                insertOp(connection, household, 1, "friends", device, member)
            }
        }
        assertThrows<SQLException> {
            db.tenant(household) { connection ->
                insertWrap(connection, household, "family", device)
            }
        }
    }

    @Test
    fun `an op id is unique per household and a seq is unique per household`() {
        val household = newHousehold()
        val device = newDevice()
        val member = UUID.randomUUID().toString()
        seedHousehold(db, household, device, member)
        val opId = UUID.randomUUID().toString()
        db.tenant(household) { connection ->
            insertOp(connection, household, 1, "family", device, member, opId)
        }

        assertThrows<SQLException> {
            db.tenant(household) { connection ->
                insertOp(connection, household, 2, "family", device, member, opId)
            }
        }
        assertThrows<SQLException> {
            db.tenant(household) { connection ->
                insertOp(connection, household, 1, "family", device, member)
            }
        }
    }

    @Test
    fun `members gain a nullable family role limited to parent teen and child`() {
        val household = newHousehold()
        val member = UUID.randomUUID().toString()
        seedHousehold(db, household, newDevice(), member)

        val initial = db.tenant(household) { connection ->
            connection.strings("select coalesce(family_role, 'null') from members")
        }
        for (role in listOf("parent", "teen", "child")) {
            db.tenant(household) { connection -> setRole(connection, member, role) }
        }

        assertEquals(listOf("null"), initial)
        assertThrows<SQLException> {
            db.tenant(household) { connection -> setRole(connection, member, "owner") }
        }
    }

    @Test
    fun `a device delete would cascade its cursors and wraps but never its ops`() {
        val definitions = PostgresHarness.adminDatabase().dataSource.connection.use { connection ->
            connection.strings(
                "select conname || ' ' || pg_get_constraintdef(oid) from pg_constraint " +
                    "where conname in ('board_cursors_device_id_fkey', " +
                    "'scope_key_wraps_device_id_fkey', 'scope_key_wraps_wrapper_device_id_fkey', " +
                    "'board_ops_from_device_fkey') order by conname",
            )
        }

        assertEquals(4, definitions.size, definitions.toString())
        assertTrue(definitions[0].startsWith("board_cursors") && definitions[0].contains("ON DELETE CASCADE"))
        assertTrue(definitions[1].startsWith("board_ops") && !definitions[1].contains("ON DELETE"))
        assertTrue(definitions[2].contains("device_id_fkey") && definitions[2].contains("ON DELETE CASCADE"))
        assertTrue(definitions[3].contains("wrapper") && definitions[3].contains("ON DELETE SET NULL"))
    }

    private fun seedBoardRows(connection: Connection, household: String, device: String, member: String) {
        connection.prepareStatement(
            "insert into board_heads(household_id, last_seq) values (?::uuid, 1)",
        ).use {
            it.setString(1, household)
            it.executeUpdate()
        }
        insertOp(connection, household, 1, "family", device, member)
        connection.prepareStatement(
            "insert into board_cursors(household_id, device_id, scope, last_seq) " +
                "values (?::uuid, ?::uuid, 'family', 1)",
        ).use {
            it.setString(1, household)
            it.setString(2, device)
            it.executeUpdate()
        }
        insertWrap(connection, household, "parents", device)
    }

    private fun insertOp(
        connection: Connection,
        household: String,
        seq: Long,
        scope: String,
        device: String,
        member: String,
        opId: String = UUID.randomUUID().toString(),
    ) {
        connection.prepareStatement(
            """
            insert into board_ops(household_id, seq, scope, op_id, from_device, member_id,
                                  key_epoch, body, body_hash, size)
            values (?::uuid, ?, ?, ?::uuid, ?::uuid, ?::uuid, 0, ?, ?, 4)
            """.trimIndent(),
        ).use {
            it.setString(1, household)
            it.setLong(2, seq)
            it.setString(3, scope)
            it.setString(4, opId)
            it.setString(5, device)
            it.setString(6, member)
            it.setBytes(7, ByteArray(4) { 1 })
            it.setBytes(8, ByteArray(32) { 2 })
            it.executeUpdate()
        }
    }

    private fun insertWrap(connection: Connection, household: String, scope: String, device: String) {
        connection.prepareStatement(
            "insert into scope_key_wraps(household_id, scope, key_epoch, device_id, wrapped_key, " +
                "wrapper_device_id) values (?::uuid, ?, 0, ?::uuid, ?, ?::uuid)",
        ).use {
            it.setString(1, household)
            it.setString(2, scope)
            it.setString(3, device)
            it.setBytes(4, ByteArray(60) { 5 })
            it.setString(5, device)
            it.executeUpdate()
        }
    }

    private fun setRole(connection: Connection, member: String, role: String) {
        connection.prepareStatement("update members set family_role = ? where id = ?::uuid").use {
            it.setString(1, role)
            it.setString(2, member)
            it.executeUpdate()
        }
    }

    private fun Connection.count(sql: String): Int =
        createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }

    private fun Connection.strings(sql: String): List<String> =
        createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                buildList { while (rows.next()) add(rows.getString(1)) }
            }
        }
}

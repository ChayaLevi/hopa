package com.hopa.backend.store

import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.harness.newDevice
import com.hopa.backend.harness.newHousehold
import com.hopa.backend.harness.seedHousehold
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TenantIsolationTest {
    private val db = PostgresHarness.appDatabase()
    private val devices = mutableMapOf<String, String>()

    private fun twoHouseholds(): Pair<String, String> {
        val home = newHousehold()
        val other = newHousehold()
        for (household in listOf(home, other)) {
            val device = newDevice()
            seedHousehold(db, household, device, UUID.randomUUID().toString())
            devices[household] = device
        }
        return home to other
    }

    private fun deviceOf(household: String): String = devices.getValue(household)

    private fun seedWrap(household: String) {
        db.tenant(household) { connection ->
            connection.prepareStatement(
                "insert into household_key_wraps(household_id, device_id, wrapped_key) " +
                    "values (?::uuid, ?::uuid, ?)",
            ).use {
                it.setString(1, household)
                it.setString(2, deviceOf(household))
                it.setBytes(3, ByteArray(60) { 9 })
                it.executeUpdate()
            }
        }
    }

    private fun seedPending(household: String, id: String, codeHash: ByteArray) {
        db.tenant(household) { connection ->
            connection.prepareStatement(
                """
                insert into pending_pairings(id, code_hash, platform, name, public_key, alg,
                                             household_id, expires_at)
                values (?::uuid, ?, 'windows', 'Desk', ?, 'x25519', ?::uuid,
                        now() + interval '5 minutes')
                """.trimIndent(),
            ).use {
                it.setString(1, id)
                it.setBytes(2, codeHash)
                it.setBytes(3, ByteArray(32) { 4 })
                it.setString(4, household)
                it.executeUpdate()
            }
        }
    }

    private fun seedTask(household: String, messageId: String): String {
        val task = UUID.randomUUID().toString()
        val device = deviceOf(household)
        db.tenant(household) { connection ->
            val member = connection.createStatement().use { statement ->
                statement.executeQuery("select id from members limit 1").use { rows ->
                    rows.next()
                    rows.getString(1)
                }
            }
            connection.prepareStatement(
                """
                insert into tasks(id, household_id, member_id, from_device, to_device,
                                  message_id, state, trace_id, body, deadline_at)
                values (?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid, ?::uuid,
                        'queued', ?, ?, now() + interval '10 minutes')
                """.trimIndent(),
            ).use {
                it.setString(1, task)
                it.setString(2, household)
                it.setString(3, member)
                it.setString(4, device)
                it.setString(5, device)
                it.setString(6, messageId)
                it.setString(7, "a".repeat(32))
                it.setBytes(8, "{}".toByteArray())
                it.executeUpdate()
            }
        }
        return task
    }

    @Test
    fun `a cross-household select by id returns zero rows`() {
        val (home, other) = twoHouseholds()

        val seen = db.tenant(home) { connection ->
            connection.prepareStatement("select count(*) from devices where id = ?::uuid").use {
                it.setString(1, deviceOf(other))
                it.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
            }
        }

        assertEquals(0, seen)
    }

    @Test
    fun `a token row can only be read by the caller holding it`() {
        val (home, _) = twoHouseholds()
        val hash = ByteArray(32) { 7 }
        db.tenant(home) { connection ->
            connection.prepareStatement(
                "insert into access_tokens(id, device_id, household_id, token_hash, expires_at) " +
                    "values (gen_random_uuid(), ?::uuid, ?::uuid, ?, now() + interval '15 minutes')",
            ).use {
                it.setString(1, deviceOf(home))
                it.setString(2, home)
                it.setBytes(3, hash)
                it.executeUpdate()
            }
        }

        val byPresentedHash = db.token(hex(hash)) { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from access_tokens").use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }
        val byOtherHash = db.token("00".repeat(32)) { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from access_tokens").use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }

        assertEquals(1, byPresentedHash)
        assertEquals(0, byOtherHash)
    }

    @Test
    fun `a thousand cross-household reads leak nothing`() {
        val attempts = System.getenv("HOPA_ISOLATION_ATTEMPTS")?.toIntOrNull() ?: 1_000
        val (home, other) = twoHouseholds()
        val foreign = (1..50).map { seedTask(other, UUID.randomUUID().toString()) }
        val foreignPending = (1..50).map { index ->
            val id = UUID.randomUUID().toString()
            seedPending(other, id, ByteArray(32) { (0x40 + index).toByte() })
            id
        }
        seedWrap(other)
        val foreignDevice = deviceOf(other)
        var checked = 0

        repeat(attempts) {
            val target = foreign.random()
            val pending = foreignPending.random()
            val (taskSeen, pendingSeen, wrapSeen) = db.tenant(home) { connection ->
                Triple(
                    connection.prepareStatement("select count(*) from tasks where id = ?::uuid").use {
                        it.setString(1, target)
                        it.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
                    },
                    connection.prepareStatement(
                        "select count(*) from pending_pairings where id = ?::uuid",
                    ).use {
                        it.setString(1, pending)
                        it.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
                    },
                    connection.prepareStatement(
                        "select count(*) from household_key_wraps where device_id = ?::uuid",
                    ).use {
                        it.setString(1, foreignDevice)
                        it.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
                    },
                )
            }
            assertEquals(0, taskSeen)
            assertEquals(0, pendingSeen)
            assertEquals(0, wrapSeen)
            checked++
        }

        assertEquals(attempts, checked)
    }

    private fun memberOf(household: String): String =
        db.tenant(household) { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select id from members limit 1").use { rows ->
                    rows.next()
                    rows.getString(1)
                }
            }
        }

    private fun insertBoardRows(connection: Connection, household: String, device: String, member: String) {
        connection.prepareStatement(
            "insert into board_heads(household_id, last_seq) values (?::uuid, 1)",
        ).use {
            it.setString(1, household)
            it.executeUpdate()
        }
        connection.prepareStatement(
            """
            insert into board_ops(household_id, seq, scope, op_id, from_device, member_id,
                                  key_epoch, body, body_hash, size)
            values (?::uuid, 1, 'family', gen_random_uuid(), ?::uuid, ?::uuid, 0, ?, ?, 3)
            """.trimIndent(),
        ).use {
            it.setString(1, household)
            it.setString(2, device)
            it.setString(3, member)
            it.setBytes(4, ByteArray(3) { 1 })
            it.setBytes(5, ByteArray(32) { 2 })
            it.executeUpdate()
        }
        connection.prepareStatement(
            "insert into board_cursors(household_id, device_id, scope, last_seq) " +
                "values (?::uuid, ?::uuid, 'family', 1)",
        ).use {
            it.setString(1, household)
            it.setString(2, device)
            it.executeUpdate()
        }
        connection.prepareStatement(
            "insert into scope_key_wraps(household_id, scope, key_epoch, device_id, wrapped_key) " +
                "values (?::uuid, 'parents', 0, ?::uuid, ?)",
        ).use {
            it.setString(1, household)
            it.setString(2, device)
            it.setBytes(3, ByteArray(60) { 9 })
            it.executeUpdate()
        }
    }

    private fun Connection.changed(sql: String, household: String): Int =
        prepareStatement(sql).use {
            it.setString(1, household)
            it.executeUpdate()
        }

    @Test
    fun `a household can neither read nor write another household's board`() {
        val (home, other) = twoHouseholds()
        db.tenant(other) { connection -> insertBoardRows(connection, other, deviceOf(other), memberOf(other)) }
        val tables = listOf("board_heads", "board_ops", "board_cursors", "scope_key_wraps")

        val seen = db.tenant(home) { connection ->
            tables.map { table ->
                connection.prepareStatement("select count(*) from $table where household_id = ?::uuid").use {
                    it.setString(1, other)
                    it.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
                }
            }
        }
        val updated = db.tenant(home) { connection ->
            listOf(
                connection.changed("update board_heads set last_seq = 99 where household_id = ?::uuid", other),
                connection.changed(
                    "update board_ops set body = decode('00', 'hex') where household_id = ?::uuid",
                    other,
                ),
                connection.changed("update board_cursors set last_seq = 99 where household_id = ?::uuid", other),
                connection.changed(
                    "update scope_key_wraps set wrapped_key = decode('00', 'hex') where household_id = ?::uuid",
                    other,
                ),
            )
        }
        val deleted = db.tenant(home) { connection ->
            tables.map { table -> connection.changed("delete from $table where household_id = ?::uuid", other) }
        }
        // Writing rows that name the other household is refused by the policy's check.
        assertThrows<SQLException> {
            db.tenant(home) { connection -> insertBoardRows(connection, other, deviceOf(home), memberOf(home)) }
        }
        val stillThere = db.tenant(other) { connection ->
            tables.map { table ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("select count(*) from $table").use { rows -> rows.next(); rows.getInt(1) }
                }
            }
        }
        val unchanged = db.tenant(other) { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select last_seq from board_heads").use { rows -> rows.next(); rows.getLong(1) }
            }
        }

        assertEquals(listOf(0, 0, 0, 0), seen)
        assertEquals(listOf(0, 0, 0, 0), updated)
        assertEquals(listOf(0, 0, 0, 0), deleted)
        assertEquals(listOf(1, 1, 1, 1), stillThere)
        assertEquals(1L, unchanged)
    }

    @Test
    fun `the table owner is forced through the policy too`() {
        val owner = PostgresHarness.ownerDatabase()
        val household = newHousehold()
        seedHousehold(owner, household, newDevice(), UUID.randomUUID().toString())

        val visible = owner.dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from devices").use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }

        assertEquals(0, visible)
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}

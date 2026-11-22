package com.hopa.backend.store

import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.harness.newDevice
import com.hopa.backend.harness.newHousehold
import com.hopa.backend.harness.seedHousehold
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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

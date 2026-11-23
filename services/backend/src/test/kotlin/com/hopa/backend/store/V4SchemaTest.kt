package com.hopa.backend.store

import com.hopa.backend.harness.PostgresHarness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Schema v4: a pending pairing remembers which device vouched for it, so the
 * claim can return that device's public key and the PC can derive the same
 * static-static KEK the phone used to wrap the household key.
 */
class V4SchemaTest {
    @Test
    fun `v4 adds a nullable voucher device reference and re-applies cleanly`() {
        val owner = PostgresHarness.ownerDatabase()

        assertEquals(0, owner.migrate())
        assertEquals(0, owner.migrate())

        val column = PostgresHarness.adminDatabase().dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select data_type, is_nullable, column_default
                from information_schema.columns
                where table_schema = 'public' and table_name = 'pending_pairings'
                  and column_name = 'voucher_device_id'
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { rows ->
                    if (!rows.next()) {
                        null
                    } else {
                        Triple(rows.getString(1), rows.getString(2), rows.getString(3))
                    }
                }
            }
        }

        assertEquals("uuid", column?.first)
        assertEquals("YES", column?.second)
        assertEquals(null, column?.third)
    }

    @Test
    fun `the voucher reference cascades a device delete to null`() {
        val definition = PostgresHarness.adminDatabase().dataSource.connection.use { connection ->
            connection.prepareStatement(
                "select pg_get_constraintdef(oid) from pg_constraint " +
                    "where conname = 'pending_pairings_voucher_device_id_fkey'",
            ).use { statement ->
                statement.executeQuery().use { rows ->
                    if (rows.next()) rows.getString(1) else null
                }
            }
        }

        assertTrue(definition?.contains("REFERENCES devices(id)") == true, definition.orEmpty())
        assertTrue(definition?.contains("ON DELETE SET NULL") == true, definition.orEmpty())
    }
}

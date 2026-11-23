package com.hopa.backend.store

import com.hopa.backend.harness.PostgresHarness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Schema v3: a vouched pending pairing parks the household key wrap on the
 * pending row, because `household_key_wraps.device_id` is a NOT NULL FK to a
 * device that does not exist until the PC claims. The wrap is ciphertext and
 * tenant-visible only.
 */
class V3SchemaTest {
    @Test
    fun `v3 adds a nullable wrapped key to pending pairings and re-applies cleanly`() {
        val owner = PostgresHarness.ownerDatabase()

        assertEquals(0, owner.migrate())
        assertEquals(0, owner.migrate())

        val column = PostgresHarness.adminDatabase().dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                select data_type, is_nullable, column_default
                from information_schema.columns
                where table_schema = 'public' and table_name = 'pending_pairings'
                  and column_name = 'wrapped_key'
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

        assertEquals("bytea", column?.first)
        assertEquals("YES", column?.second)
        assertNull(column?.third)
    }

    @Test
    fun `pending pairings stay forced row level secured after v3`() {
        val (secured, forced) = PostgresHarness.adminDatabase().dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    select relrowsecurity, relforcerowsecurity
                    from pg_class
                    where relname = 'pending_pairings'
                    """.trimIndent(),
                ).use { rows ->
                    rows.next()
                    rows.getBoolean(1) to rows.getBoolean(2)
                }
            }
        }

        assertTrue(secured)
        assertTrue(forced)
    }
}

package com.hopa.backend.store

import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.harness.newDevice
import com.hopa.backend.harness.newHousehold
import com.hopa.backend.harness.seedHousehold
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Schema v7: member permissions replace V6's family role (B5). The table is
 * household scoped with RLS enabled and forced; the upgrade turns parents into
 * `board.private` holders and gives each household's founder both permissions.
 */
class V7SchemaTest {
    private val db = PostgresHarness.appDatabase()

    @Test
    fun `member permissions have row level security enabled and forced, and members keep theirs`() {
        val flags = PostgresHarness.adminDatabase().dataSource.connection.use { connection ->
            connection.strings(
                "select relname || ':' || relrowsecurity || ':' || relforcerowsecurity " +
                    "from pg_class where relname in ('member_permissions', 'members') order by relname",
            )
        }

        assertEquals(listOf("member_permissions:true:true", "members:true:true"), flags)
    }

    @Test
    fun `the family role column is gone`() {
        val columns = PostgresHarness.adminDatabase().dataSource.connection.use { connection ->
            connection.strings(
                "select column_name from information_schema.columns " +
                    "where table_name = 'members' and column_name = 'family_role'",
            )
        }

        assertEquals(emptyList<String>(), columns)
    }

    @Test
    fun `only known permissions are stored, once per member`() {
        val household = newHousehold()
        val member = UUID.randomUUID().toString()
        seedHousehold(db, household, newDevice(), member)

        db.tenant(household) { connection -> grant(connection, household, member, "board.private") }

        assertThrows<SQLException> { db.tenant(household) { connection -> grant(connection, household, member, "parent") } }
        assertThrows<SQLException> { db.tenant(household) { connection -> grant(connection, household, member, "board.private") } }
    }

    @Test
    fun `a household neither sees nor writes another household's permissions`() {
        val home = newHousehold()
        val other = newHousehold()
        val homeMember = UUID.randomUUID().toString()
        val otherMember = UUID.randomUUID().toString()
        seedHousehold(db, home, newDevice(), homeMember)
        seedHousehold(db, other, newDevice(), otherMember)
        db.tenant(other) { connection -> grant(connection, other, otherMember, "group.manage") }

        val seen = db.tenant(home) { connection -> connection.strings("select permission from member_permissions") }
        val deleted = db.tenant(home) { connection ->
            connection.createStatement().use { it.executeUpdate("delete from member_permissions") }
        }

        assertEquals(emptyList<String>(), seen)
        assertEquals(0, deleted)
        assertThrows<SQLException> { db.tenant(home) { connection -> grant(connection, other, otherMember, "board.private") } }
        assertEquals(listOf("group.manage"), db.tenant(other) { it.strings("select permission from member_permissions") })
    }

    @Test
    fun `the upgrade turns parents into private holders and gives each founder both permissions`() {
        // As the deploy role: a non-superuser that owns the tables, which FORCE RLS binds.
        val name = PostgresHarness.scratchDatabase()
        PostgresHarness.scratchConnect(name, "hopa_owner").use { owner ->
            migrate(owner, "6")
            val home = UUID.randomUUID().toString()
            val bare = UUID.randomUUID().toString()
            val founder = UUID.randomUUID().toString()
            val laterOwner = UUID.randomUUID().toString()
            val parent = UUID.randomUUID().toString()
            val teen = UUID.randomUUID().toString()
            val plain = UUID.randomUUID().toString()
            owner.tenant(home) { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("insert into households(id, name) values ('$home', 'home')")
                    statement.execute(
                        """
                        insert into members(id, household_id, role, display_name, family_role, created_at) values
                          ('$founder', '$home', 'owner', 'Founder', null, now() - interval '2 days'),
                          ('$laterOwner', '$home', 'owner', 'Later', null, now() - interval '1 day'),
                          ('$parent', '$home', 'member', 'Parent', 'parent', now()),
                          ('$teen', '$home', 'member', 'Teen', 'teen', now())
                        """.trimIndent(),
                    )
                }
            }
            owner.tenant(bare) { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("insert into households(id, name) values ('$bare', 'bare')")
                    statement.execute("insert into members(id, household_id, role, display_name) values ('$plain', '$bare', 'member', 'Plain')")
                }
            }

            migrate(owner, null)
        }
        PostgresHarness.scratchConnect(name, "postgres").use { admin ->
            val granted = admin.dataSource.connection.use { connection ->
                connection.strings(
                    "select m.display_name || ':' || string_agg(p.permission, ',' order by p.permission) " +
                        "from member_permissions p join members m on m.id = p.member_id " +
                        "group by m.display_name order by m.display_name",
                )
            }
            assertEquals(listOf("Founder:board.private,group.manage", "Parent:board.private"), granted)
            val forced = admin.dataSource.connection.use { connection ->
                connection.strings("select relforcerowsecurity::text from pg_class where relname = 'members'")
            }
            assertEquals(listOf("true"), forced, "the upgrade restores FORCE RLS on members")
        }
    }

    private fun migrate(database: Database, target: String?) {
        Flyway.configure()
            .dataSource(database.dataSource)
            .locations("classpath:db/migration")
            .apply { if (target != null) target(target) }
            .load()
            .migrate()
    }

    private fun grant(connection: Connection, household: String, member: String, permission: String) {
        connection.prepareStatement(
            "insert into member_permissions(household_id, member_id, permission) values (?::uuid, ?::uuid, ?)",
        ).use {
            it.setString(1, household)
            it.setString(2, member)
            it.setString(3, permission)
            it.executeUpdate()
        }
    }

    private fun Connection.strings(sql: String): List<String> =
        createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                buildList { while (rows.next()) add(rows.getString(1)) }
            }
        }
}

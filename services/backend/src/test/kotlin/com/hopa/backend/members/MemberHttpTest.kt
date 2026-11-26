package com.hopa.backend.members

import com.hopa.backend.ServerConfig
import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.board.BoardHouseholds
import com.hopa.backend.board.BoardHouseholds.CHILD
import com.hopa.backend.board.BoardHouseholds.MANAGER
import com.hopa.backend.board.BoardHouseholds.PARENT
import com.hopa.backend.harness.MapAuth
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.module
import com.hopa.backend.store.PgBoardStore
import com.hopa.backend.store.PgDevices
import com.hopa.backend.store.PgMemberStore
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/** `GET /v1/members` and `PUT /v1/members/{id}/permissions` on Postgres (B5). */
class MemberHttpTest {
    private val db = PostgresHarness.appDatabase()

    private fun config(vararg groups: List<DeviceIdentity>) = ServerConfig(
        version = "0.1.0",
        auth = MapAuth(groups.flatMap { it }.associateBy { "tok-${it.deviceId}" }),
        devices = PgDevices(db),
        boardStore = PgBoardStore(db),
        members = PgMemberStore(db),
    )

    private suspend fun HttpClient.membersOf(caller: DeviceIdentity): HttpResponse =
        get("/v1/members") { header("Authorization", "Bearer tok-${caller.deviceId}") }

    private suspend fun HttpClient.setPermissions(caller: DeviceIdentity, memberId: String, json: String): HttpResponse =
        put("/v1/members/$memberId/permissions") {
            header("Authorization", "Bearer tok-${caller.deviceId}")
            contentType(ContentType.Application.Json)
            setBody(json)
        }

    private suspend fun HttpResponse.json(): JsonObject = Json.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun HttpResponse.code(): String = json()["code"]!!.jsonPrimitive.content

    /** Member id to its permissions, as the list answers them. */
    private suspend fun HttpClient.permissionsSeenBy(caller: DeviceIdentity): Map<String, List<String>> =
        membersOf(caller).json()["members"]!!.jsonArray.associate { member ->
            val fields = member.jsonObject
            fields["id"]!!.jsonPrimitive.content to fields["permissions"]!!.jsonArray.map { it.jsonPrimitive.content }
        }

    @Test
    fun `any device lists its own group's members with their permissions`() = testApplication {
        val (mom, dad, kid) = BoardHouseholds.seed(listOf(MANAGER, PARENT, CHILD))
        val other = BoardHouseholds.seed(listOf(MANAGER))
        application { module(config(listOf(mom, dad, kid), other)) }

        val response = client.membersOf(kid)

        assertEquals(HttpStatusCode.OK, response.status)
        val members = response.json()["members"]!!.jsonArray.map { it.jsonObject }
        assertEquals(3, members.size)
        assertFalse(members.any { it["hasAccount"]!!.jsonPrimitive.boolean }, "seeded members have no account")
        assertEquals(
            mapOf(
                mom.memberId to listOf("board.private", "group.manage"),
                dad.memberId to listOf("board.private"),
                kid.memberId to emptyList(),
            ),
            client.permissionsSeenBy(kid),
        )
        assertFalse(response.bodyAsText().contains(other.single().memberId), "never another group's member")
    }

    @Test
    fun `a manager sets a member's permissions and gets the member back`() = testApplication {
        val family = BoardHouseholds.seed(listOf(MANAGER, PARENT, CHILD))
        val (mom, _, kid) = family
        application { module(config(family)) }

        val response = client.setPermissions(mom, kid.memberId, """{"permissions":["board.private"]}""")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.json()
        assertEquals(kid.memberId, body["id"]!!.jsonPrimitive.content)
        assertEquals(listOf("board.private"), body["permissions"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(listOf("board.private"), client.permissionsSeenBy(kid)[kid.memberId])
    }

    @Test
    fun `only a member who manages the group changes permissions`() = testApplication {
        val family = BoardHouseholds.seed(listOf(MANAGER, PARENT, CHILD))
        val (_, dad, kid) = family
        application { module(config(family)) }

        val byParent = client.setPermissions(dad, kid.memberId, """{"permissions":["board.private"]}""")
        val bySelf = client.setPermissions(kid, kid.memberId, """{"permissions":["group.manage"]}""")

        assertEquals(HttpStatusCode.Forbidden, byParent.status)
        assertEquals("E_SCOPE", byParent.code())
        assertEquals(HttpStatusCode.Forbidden, bySelf.status)
        assertEquals(emptyList<String>(), client.permissionsSeenBy(kid)[kid.memberId])
    }

    @Test
    fun `a member of another group is not found and stays unchanged`() = testApplication {
        val family = BoardHouseholds.seed(listOf(MANAGER, CHILD))
        val other = BoardHouseholds.seed(listOf(MANAGER, CHILD))
        application { module(config(family, other)) }

        val response = client.setPermissions(family[0], other[1].memberId, """{"permissions":["group.manage"]}""")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("E_TENANT", response.code())
        assertEquals(emptyList<String>(), client.permissionsSeenBy(other[0])[other[1].memberId])
    }

    @Test
    fun `malformed requests are E_SCHEMA and a missing token is E_AUTH`() = testApplication {
        val family = BoardHouseholds.seed(listOf(MANAGER, CHILD))
        val (mom, kid) = family
        application { module(config(family)) }

        val answers = listOf(
            client.setPermissions(mom, kid.memberId, """{"permissions":["parent"]}"""),
            client.setPermissions(mom, kid.memberId, """{"permissions":["board.private","board.private"]}"""),
            client.setPermissions(mom, "not-a-uuid", """{"permissions":[]}"""),
            client.setPermissions(mom, kid.memberId, """{}"""),
        )

        answers.forEach {
            assertEquals(HttpStatusCode.BadRequest, it.status)
            assertEquals("E_SCHEMA", it.code())
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/members").status)
        assertEquals(emptyList<String>(), client.permissionsSeenBy(kid)[kid.memberId])
    }

    @Test
    fun `the last manager cannot step down, one of two can`() = testApplication {
        val family = BoardHouseholds.seed(listOf(MANAGER, PARENT))
        val (mom, dad) = family
        application { module(config(family)) }

        val alone = client.setPermissions(mom, mom.memberId, """{"permissions":["board.private"]}""")
        val promoted = client.setPermissions(mom, dad.memberId, """{"permissions":["board.private","group.manage"]}""")
        val stepped = client.setPermissions(mom, mom.memberId, """{"permissions":["board.private"]}""")
        val dadAlone = client.setPermissions(dad, dad.memberId, """{"permissions":[]}""")

        assertEquals(HttpStatusCode.Conflict, alone.status)
        assertEquals("E_CONFLICT", alone.code())
        assertEquals(HttpStatusCode.OK, promoted.status)
        assertEquals(HttpStatusCode.OK, stepped.status)
        assertEquals(HttpStatusCode.Conflict, dadAlone.status)
        assertEquals(
            mapOf(mom.memberId to listOf("board.private"), dad.memberId to listOf("board.private", "group.manage")),
            client.permissionsSeenBy(mom),
        )
    }
}

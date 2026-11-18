package com.hopa.backend.http

import com.hopa.backend.ServerConfig
import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.harness.MapAuth
import com.hopa.backend.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeHttpTest {
    private val device = DeviceIdentity("dev-phone", "hh-1", "mem-1")

    private fun config(): ServerConfig = ServerConfig(
        version = "0.1.0",
        auth = MapAuth(mapOf("tok-dev-phone" to device)),
    )

    @Test
    fun `the bundle needs a paired token`() = testApplication {
        application { module(config()) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/knowledge").status)
    }

    @Test
    fun `a paired device gets the bundle and its etag`() = testApplication {
        application { module(config()) }

        val response = client.get("/v1/knowledge") {
            header(HttpHeaders.Authorization, "Bearer tok-dev-phone")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertEquals(1, body["schemaVersion"]!!.jsonPrimitive.int)
        assertTrue(body["version"]!!.jsonPrimitive.content.startsWith("sha256:"))
        assertTrue(body["catalog"]!!.jsonArray.isNotEmpty())
        assertTrue(response.headers[HttpHeaders.ETag]!!.startsWith("\"sha256:"))
    }

    @Test
    fun `a matching etag answers 304`() = testApplication {
        application { module(config()) }

        val etag = client.get("/v1/knowledge") {
            header(HttpHeaders.Authorization, "Bearer tok-dev-phone")
        }.headers[HttpHeaders.ETag]!!

        val again = client.get("/v1/knowledge") {
            header(HttpHeaders.Authorization, "Bearer tok-dev-phone")
            header(HttpHeaders.IfNoneMatch, etag)
        }

        assertEquals(HttpStatusCode.NotModified, again.status)
    }
}

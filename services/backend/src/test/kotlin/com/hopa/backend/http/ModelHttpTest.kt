package com.hopa.backend.http

import com.hopa.backend.ServerConfig
import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.harness.MapAuth
import com.hopa.backend.module
import com.hopa.contracts.json.HopaJson
import com.hopa.inference.local.ModelManifest
import com.hopa.inference.local.ModelSpec
import com.hopa.inference.local.LocalModelCatalogue
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ModelHttpTest {
    @TempDir
    lateinit var dir: File

    private val device = DeviceIdentity("dev-phone", "hh-1", "mem-1")

    private fun config(modelsDir: String = "") = ServerConfig(
        version = "0.1.0",
        auth = MapAuth(mapOf("tok-dev-phone" to device)),
        modelsDir = modelsDir,
    )

    @Test
    fun `the manifest needs a paired token`() = testApplication {
        application { module(config()) }

        assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/models").status)
    }

    @Test
    fun `an unconfigured server answers an empty manifest`() = testApplication {
        application { module(config()) }

        val response = client.get("/v1/models") {
            header(HttpHeaders.Authorization, "Bearer tok-dev-phone")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        assertTrue(body["models"]!!.jsonArray.isEmpty())
        assertEquals("sha256:empty", body["version"]!!.jsonPrimitive.content)
        assertTrue(response.headers[HttpHeaders.ETag]!!.startsWith("\""))
    }

    @Test
    fun `a matching etag answers 304`() = testApplication {
        application { module(config()) }

        val etag = client.get("/v1/models") {
            header(HttpHeaders.Authorization, "Bearer tok-dev-phone")
        }.headers[HttpHeaders.ETag]!!

        val again = client.get("/v1/models") {
            header(HttpHeaders.Authorization, "Bearer tok-dev-phone")
            header(HttpHeaders.IfNoneMatch, etag)
        }

        assertEquals(HttpStatusCode.NotModified, again.status)
    }

    @Test
    fun `a served model is listed and downloadable`() = testApplication {
        val bytes = "GGUF served model".toByteArray()
        File(dir, "qwen.gguf").writeBytes(bytes)
        val spec = LocalModelCatalogue.models.first { it.id == "qwen3-1.7b" }.copy(
            file = "qwen.gguf",
            sha256 = "a".repeat(64),
        )
        val manifest = ModelManifest(version = "sha256:ignored", models = listOf(spec))
        File(dir, "models.v1.json").writeText(
            HopaJson.element.encodeToString(ModelManifest.serializer(), manifest),
        )
        application { module(config(dir.absolutePath)) }

        val listed = client.get("/v1/models") {
            header(HttpHeaders.Authorization, "Bearer tok-dev-phone")
        }
        val body = Json.parseToJsonElement(listed.bodyAsText()).jsonObject
        assertEquals(1, body["models"]!!.jsonArray.size)

        val blob = client.get("/v1/models/qwen3-1.7b") {
            header(HttpHeaders.Authorization, "Bearer tok-dev-phone")
        }
        assertEquals(HttpStatusCode.OK, blob.status)
        assertEquals(bytes.size, blob.bodyAsText().length)

        val missing = client.get("/v1/models/not-a-model") {
            header(HttpHeaders.Authorization, "Bearer tok-dev-phone")
        }
        assertEquals(HttpStatusCode.NotFound, missing.status)
    }
}

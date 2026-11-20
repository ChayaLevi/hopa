package com.hopa.backend.link

import com.hopa.contracts.json.HopaJson
import com.hopa.inference.local.LocalModelCatalogue
import com.hopa.inference.local.ModelManifest
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ModelClientTest {
    private val manifest = ModelManifest(
        version = "sha256:abc",
        models = listOf(LocalModelCatalogue.models.first()),
    )

    @Test
    fun a_200_returns_the_manifest_and_sends_the_known_version() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(HopaJson.element.encodeToString(ModelManifest.serializer(), manifest)),
            )
            val client = ModelClient(server.url("/").toString(), "tok-dev", OkHttpClient())

            val fetched = client.manifest("sha256:old")

            assertEquals(manifest, fetched)
            val request = server.takeRequest()
            assertEquals("/v1/models", request.path)
            assertEquals("Bearer tok-dev", request.getHeader("Authorization"))
            assertEquals("\"sha256:old\"", request.getHeader("If-None-Match"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun a_304_is_null_and_keeps_the_local_catalogue() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(304))
            val client = ModelClient(server.url("/").toString(), "tok-dev", OkHttpClient())

            assertNull(client.manifest("sha256:current"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun an_error_is_null_and_keeps_the_local_catalogue() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(404))
            val client = ModelClient(server.url("/").toString(), "tok-dev", OkHttpClient())

            assertNull(client.manifest(""))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun a_document_this_build_cannot_read_is_null() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setBody("""{"schemaVersion":99,"surprise":true}"""))
            val client = ModelClient(server.url("/").toString(), "tok-dev", OkHttpClient())

            assertNull(client.manifest(""))
        } finally {
            server.shutdown()
        }
    }
}

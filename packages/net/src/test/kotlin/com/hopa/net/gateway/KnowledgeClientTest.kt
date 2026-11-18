package com.hopa.net.gateway

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class KnowledgeClientTest {
    @Test
    fun a_200_returns_the_bundle_and_sends_the_known_version() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"schemaVersion":1,"version":"sha256:abc"}"""),
            )
            val client = KnowledgeClient(server.url("/").toString(), "tok-dev", OkHttpClient())

            val text = client.fetch("sha256:old")

            assertEquals("""{"schemaVersion":1,"version":"sha256:abc"}""", text)
            val request = server.takeRequest()
            assertEquals("/v1/knowledge", request.path)
            assertEquals("Bearer tok-dev", request.getHeader("Authorization"))
            assertEquals("\"sha256:old\"", request.getHeader("If-None-Match"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun a_304_is_null_and_keeps_the_copy() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(304))
            val client = KnowledgeClient(server.url("/").toString(), "tok-dev", OkHttpClient())

            assertNull(client.fetch("sha256:current"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun an_error_is_null_and_never_throws() = runBlocking {
        val server = MockWebServer()
        try {
            server.enqueue(MockResponse().setResponseCode(500))
            val client = KnowledgeClient(server.url("/").toString(), "tok-dev", OkHttpClient())

            assertNull(client.fetch(""))
        } finally {
            server.shutdown()
        }
    }
}

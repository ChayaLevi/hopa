package com.hopa.net.llm

import com.hopa.engine.model.ModelMessage
import com.hopa.engine.model.ModelRequest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenAiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var base: String

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
        base = server.url("/").toString().trimEnd('/')
    }

    @After
    fun stop() {
        server.shutdown()
    }

    @Test
    fun a_key_alone_sends_a_real_chat_completion() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"message":{"content":"ready"}}],""" +
                    """"usage":{"prompt_tokens":3,"completion_tokens":1,"total_tokens":4}}""",
            ),
        )
        val client = ModelFactory().create(
            provider = "stub",
            endpoint = base,
            apiKey = "sk-test",
            modelName = "deepseek-chat",
        )
        assertFalse(client is StubModelClient)
        val reply = client.complete(
            ModelRequest(messages = listOf(ModelMessage("user", "ready?"))),
        )
        assertEquals("ready", reply.text)
        assertEquals(4, reply.usage.totalTokens)

        val request = server.takeRequest()
        assertEquals("/chat/completions", request.path)
        assertEquals("Bearer sk-test", request.getHeader("Authorization"))
        val body = request.body.readUtf8()
        assertTrue(body.contains("\"model\":\"deepseek-chat\""))
        // A plain OpenAI-compatible server must not receive reasoning_effort.
        assertFalse(body.contains("reasoning_effort"))
    }

    @Test
    fun an_endpoint_already_ending_in_chat_completions_is_not_doubled() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"content":"ok"}}]}"""))
        val client = OpenAiClient(
            endpoint = "$base/chat/completions",
            apiKey = "sk-test",
            modelName = "m",
        )
        client.complete(ModelRequest(messages = listOf(ModelMessage("user", "hi"))))
        assertEquals("/chat/completions", server.takeRequest().path)
    }
}

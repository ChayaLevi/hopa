package com.hopa.inference.client

import com.hopa.contracts.model.ModelRequest
import com.hopa.inference.local.ModelNotLoaded
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelFactoryTest {
    @Test
    fun on_device_builds_the_local_model_and_never_a_cloud_client() {
        val factory = ModelFactory()

        val model = factory.create(
            provider = "on-device",
            endpoint = "https://api.deepseek.com/v1",
            apiKey = "sk-test",
            modelName = "deepseek-v4-flash",
        )

        assertTrue(
            model is LocalModel,
            "an on-device provider must not build an OpenAiClient, even with a key",
        )
    }

    @Test
    fun a_local_model_without_an_engine_says_what_is_missing() {
        val model = ModelFactory().create("on-device", "", "", "")

        assertThrows(ModelNotLoaded::class.java) {
            runBlocking { model.complete(ModelRequest(messages = emptyList())) }
        }
    }
}

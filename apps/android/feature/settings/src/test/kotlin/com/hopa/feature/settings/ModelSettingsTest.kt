package com.hopa.feature.settings

import com.hopa.inference.client.ModelFactory
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ModelSettingsTest {
    private val settings = FakeSettingsRepository()
    private val modelSettings = ModelSettings(settings, ModelFactory())

    private fun save(
        provider: String = "stub",
        endpoint: String = "https://api.deepseek.com/v1",
        model: String = "deepseek-v4-flash",
        key: String = "",
        bridgeKey: String = "",
    ) = runBlocking {
        modelSettings.save(
            provider = provider,
            endpoint = endpoint,
            model = model,
            key = key,
            notifyAnswers = true,
            bridgeUrl = "",
            bridgeKey = bridgeKey,
            useBridge = true,
        )
    }

    @Test
    fun `a key turns the demo provider into a cloud provider`() {
        val notice = save(key = "sk-test")

        assertEquals("openai", settings.current().provider)
        assertEquals("sk-test", settings.modelKeyValue)
        assertTrue(notice!!.contains("openai"))
    }

    @Test
    fun `a lan endpoint with a key becomes the lan provider`() {
        save(endpoint = "http://192.168.1.5:11434/v1", key = "sk-test")

        assertEquals("lan", settings.current().provider)
    }

    @Test
    fun `no key and no provider stays on the demo model`() {
        save(provider = "")

        assertEquals("stub", settings.current().provider)
        assertEquals("", settings.modelKeyValue)
    }

    @Test
    fun `the bridge token is stored next to the model key`() {
        save(provider = "openai", bridgeKey = "bridge-token")

        assertEquals("bridge-token", settings.bridgeTokenValue)
    }

    @Test
    fun `a key that never reaches the Keystore fails the save`() {
        settings.dropModelKey = true

        assertThrows(IOException::class.java) {
            save(key = "sk-test")
        }
    }
}

package com.hopa.providers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProviderChoiceRulesTest {
    private val rules = ProviderChoiceRules()

    @Test
    fun a_saved_key_beats_the_stub_provider() {
        val choice = rules.choose(
            provider = "stub",
            endpoint = "https://api.deepseek.com/v1",
            apiKey = "sk-test",
        )
        assertEquals(ProviderChoice.CLOUD, choice)
        assertTrue(ModelFactory().create("stub", "https://api.deepseek.com/v1", "sk-test", "m") !is StubModelClient)
    }

    @Test
    fun the_stub_provider_without_a_key_stays_the_demo() {
        assertEquals(
            ProviderChoice.DEMO,
            rules.choose("stub", "https://api.deepseek.com/v1", ""),
        )
        assertTrue(ModelFactory().create("stub", "https://api.deepseek.com/v1", "", "m") is StubModelClient)
    }

    @Test
    fun a_local_endpoint_is_the_lan_choice_even_without_a_key() {
        assertEquals(
            ProviderChoice.LAN,
            rules.choose("lan", "http://192.168.1.5:11434/v1", ""),
        )
    }

    @Test
    fun a_cloud_provider_without_a_key_is_not_a_model() {
        val state = ProviderStateResolver()
            .resolve("openai", "https://api.deepseek.com/v1", "")
        assertEquals(ProviderState.NO_MODEL, state)
    }

    @Test
    fun a_cloud_provider_with_a_key_reads_as_cloud() {
        val state = ProviderStateResolver()
            .resolve("stub", "https://api.deepseek.com/v1", "sk-test")
        assertEquals(ProviderState.CLOUD, state)
    }
}

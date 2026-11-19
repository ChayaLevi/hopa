package com.hopa.app.feature.settings

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreferencesSettingsTest {
    private val settings = FakeSettingsRepository()
    private val preferences = PreferencesSettings(settings)

    @Test
    fun `writes tone, length and the mascot`() = runBlocking {
        preferences.setTone("direct")
        preferences.setChattiness("short")
        preferences.setMascotFramed(false)

        assertEquals("direct", settings.current().tone)
        assertEquals("short", settings.current().chattiness)
        assertEquals(false, settings.current().mascotFramed)
    }

    @Test
    fun `writes the small switches`() = runBlocking {
        preferences.setNotifications(false)
        preferences.setReplaySkills(false)
        preferences.setPlanFirst(true)

        assertEquals(false, settings.current().notifyAnswers)
        assertEquals(false, settings.current().replaySkills)
        assertEquals(true, settings.current().planFirst)
    }

    @Test
    fun `trust adds and removes one package`() = runBlocking {
        preferences.setTrusted("com.example.bank", true)
        preferences.setTrusted("com.example.shop", true)
        preferences.setTrusted("com.example.bank", false)

        assertEquals(setOf("com.example.shop"), settings.current().trustedApps)
    }

    @Test
    fun `an empty package name changes nothing`() = runBlocking {
        preferences.setTrusted("", true)

        assertEquals(emptySet<String>(), settings.current().trustedApps)
    }
}

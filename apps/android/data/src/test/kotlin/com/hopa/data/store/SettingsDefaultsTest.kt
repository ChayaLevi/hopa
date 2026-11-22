package com.hopa.data.store

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SettingsDefaultsTest {
    @Test
    fun `a settings file with no gateway starts at the shipped address`() {
        val settings = DataStoreSettingsRepository.settingsFrom(emptyPreferences())

        assertEquals(
            "wss://hopa-backend-test.onrender.com/v1/gateway",
            settings.backendUrl,
        )
    }

    @Test
    fun `a stored gateway wins over the shipped address`() {
        val prefs = mutablePreferencesOf(
            DataStoreSettingsRepository.BACKEND_URL to "wss://other.example/v1/gateway",
        )

        val settings = DataStoreSettingsRepository.settingsFrom(prefs)

        assertEquals("wss://other.example/v1/gateway", settings.backendUrl)
    }
}

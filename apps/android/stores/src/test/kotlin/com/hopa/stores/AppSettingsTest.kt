package com.hopa.stores

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppSettingsTest {
    @Test
    fun `the mascot defaults to the cut-out look`() {
        assertEquals(false, AppSettings().mascotFramed)
    }

    @Test
    fun `a fresh install starts at the test gateway`() {
        assertEquals(
            "wss://hopa-backend-test.onrender.com/v1/gateway",
            AppSettings().backendUrl,
        )
    }
}

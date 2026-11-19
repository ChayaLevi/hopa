package com.hopa.stores

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AppSettingsTest {
    @Test
    fun `the mascot defaults to the cut-out look`() {
        assertEquals(false, AppSettings().mascotFramed)
    }
}

package com.hopa.app.feature.runningapps

import com.hopa.app.feature.settings.SettingsUiState
import com.hopa.data.settings.AppSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActModeTest {
    @Test
    fun auto_uses_the_hidden_screen_when_both_halves_are_ready() {
        val mode = mode(path = "auto", background = true, access = true)

        assertTrue(mode.hidden)
        assertTrue(mode.fallback)
        assertTrue(mode.hiddenReady)
    }

    @Test
    fun auto_falls_back_to_the_visible_screen_when_the_helper_is_off() {
        val mode = mode(path = "auto", background = false, access = true)

        assertTrue(mode.hidden)
        assertTrue(mode.fallback)
        assertTrue(mode.screenReady)
        assertFalse(mode.hiddenReady)
    }

    @Test
    fun hidden_also_needs_screen_access_to_read_the_tree() {
        val mode = mode(path = "background", background = true, access = false)

        assertFalse(mode.hiddenReady)
        assertFalse(mode.phoneAccess)
    }

    @Test
    fun the_visible_screen_is_the_phone_on_screen_path() {
        val mode = mode(path = "onscreen", background = true, access = true)

        assertFalse(mode.hidden)
        assertTrue(mode.screenReady)
    }

    @Test
    fun the_computer_is_never_a_phone_fallback() {
        val mode = mode(path = "auto", background = false, access = true, bridge = "http://pc:8766")

        assertFalse(mode.onComputer)
        assertTrue(mode.fallback)
        assertTrue(mode.computerPaired)
    }

    @Test
    fun the_computer_needs_a_saved_address_and_the_switch() {
        val off = mode(path = "bridge", bridge = "", useBridge = true)
        val disabled = mode(path = "bridge", bridge = "http://pc:8766", useBridge = false)
        val on = mode(path = "bridge", bridge = "http://pc:8766", useBridge = true)

        assertTrue(off.onComputer)
        assertFalse(off.computerReady)
        assertTrue(disabled.onComputer)
        assertFalse(disabled.computerReady)
        assertTrue(on.computerReady)
    }

    private fun mode(
        path: String,
        background: Boolean = false,
        access: Boolean = false,
        bridge: String = "",
        useBridge: Boolean = true,
    ): ActMode = ActMode.of(
        SettingsUiState(
            settings = AppSettings(
                screenPath = path,
                bridgeUrl = bridge,
                useBridge = useBridge,
                accessibilityAllowed = true,
            ),
            bridgeKey = if (bridge.isBlank()) "" else "token",
            phoneAccess = access,
            backgroundRunning = background,
        ),
    )
}

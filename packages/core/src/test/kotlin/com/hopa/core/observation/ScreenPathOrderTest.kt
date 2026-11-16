package com.hopa.core.observation

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenPathOrderTest {
    private val order = ScreenPathOrder()

    @Test
    fun auto_prefers_the_background_display_then_the_bridge_then_the_screen() {
        assertEquals(
            listOf("background", "bridge", "accessibility"),
            order.order(ScreenPath.AUTO, background = true, bridge = true, onscreen = true),
        )
        assertEquals(
            listOf("accessibility"),
            order.order(ScreenPath.AUTO, background = false, bridge = false, onscreen = true),
        )
    }

    @Test
    fun a_forced_path_is_tried_alone() {
        assertEquals(
            listOf("accessibility"),
            order.order(ScreenPath.ONSCREEN, background = true, bridge = true, onscreen = true),
        )
        assertEquals(
            emptyList<String>(),
            order.order(ScreenPath.BACKGROUND, background = false, bridge = true, onscreen = true),
        )
    }
}

package com.hopa.observation.produce

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScreenPathOrderTest {
    private val order = ScreenPathOrder()

    @Test
    fun auto_uses_the_phone_only() {
        assertEquals(
            listOf("background", "accessibility"),
            order.order(ScreenPath.AUTO, background = true, bridge = true, onscreen = true),
        )
        assertEquals(
            listOf("accessibility"),
            order.order(ScreenPath.AUTO, background = false, bridge = true, onscreen = true),
        )
        assertEquals(
            emptyList<String>(),
            order.order(ScreenPath.AUTO, background = false, bridge = true, onscreen = false),
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

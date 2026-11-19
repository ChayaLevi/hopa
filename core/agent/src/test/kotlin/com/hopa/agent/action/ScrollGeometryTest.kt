package com.hopa.agent.action

import com.hopa.contracts.wire.ScreenSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScrollGeometryTest {
    private val geometry = ScrollGeometry()
    private val screen = ScreenSize(width = 1080, height = 2400)

    @Test
    fun scrolling_down_moves_the_finger_up() {
        val points = geometry.swipe(screen, "down")
        assertTrue(points[1] > screen.height / 2, "start below centre")
        assertTrue(points[3] < screen.height / 2, "end above centre")
        assertEquals(points[0], points[2])
    }

    @Test
    fun scrolling_up_is_the_other_way() {
        val down = geometry.swipe(screen, "down")
        val up = geometry.swipe(screen, "up")
        assertEquals(down[1], up[3])
        assertEquals(down[3], up[1])
    }

    @Test
    fun a_sideways_swipe_follows_the_direction() {
        val left = geometry.swipe(screen, "left")
        assertTrue(left[0] > left[2])
        val right = geometry.swipe(screen, "right")
        assertTrue(right[0] < right[2])
    }

    @Test
    fun an_unknown_direction_still_scrolls_down() {
        assertEquals(
            geometry.swipe(screen, "down").toList(),
            geometry.swipe(screen, "").toList(),
        )
    }
}

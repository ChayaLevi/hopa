package com.hopa.agent.action

import com.hopa.contracts.wire.ScreenSize

/**
 * Where a scroll gesture starts and ends. The screen decides the distance, so a
 * tall phone scrolls further than a small one, and the direction is honoured
 * instead of always swiping the same way.
 */
class ScrollGeometry {
    fun swipe(screen: ScreenSize, direction: String): IntArray {
        val width = if (screen.width > 0) screen.width else 1080
        val height = if (screen.height > 0) screen.height else 2400
        val midX = width / 2
        val midY = height / 2
        val long = (height * 0.32f).toInt()
        return when (direction.lowercase()) {
            "up" -> intArrayOf(midX, midY - long, midX, midY + long)
            "left" -> intArrayOf(width - width / 5, midY, width / 5, midY)
            "right" -> intArrayOf(width / 5, midY, width - width / 5, midY)
            else -> intArrayOf(midX, midY + long, midX, midY - long)
        }
    }
}

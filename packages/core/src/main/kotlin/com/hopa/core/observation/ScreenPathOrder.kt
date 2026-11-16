package com.hopa.core.observation

/**
 * Which sources a turn should try, in order. A forced path is tried alone so a
 * comparison is honest; AUTO is the only path a normal user needs, and it keeps
 * the background display first because it leaves the screen alone.
 */
class ScreenPathOrder {
    fun order(preferred: ScreenPath, background: Boolean, bridge: Boolean, onscreen: Boolean): List<String> =
        when (preferred) {
            ScreenPath.AUTO -> buildList {
                if (background) add("background")
                if (bridge) add("bridge")
                if (onscreen) add("accessibility")
            }

            ScreenPath.BACKGROUND -> if (background) listOf("background") else emptyList()
            ScreenPath.BRIDGE -> if (bridge) listOf("bridge") else emptyList()
            ScreenPath.ONSCREEN -> if (onscreen) listOf("accessibility") else emptyList()
        }
}

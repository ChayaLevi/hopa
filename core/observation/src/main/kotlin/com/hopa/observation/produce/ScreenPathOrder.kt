package com.hopa.observation.produce

/**
 * Which sources a turn should try, in order. A forced path is tried alone so a
 * comparison is honest; AUTO is the phone's best mode — the hidden display when
 * it is up, otherwise this screen. The computer is another machine and is only
 * used when it is picked explicitly.
 */
class ScreenPathOrder {
    fun order(preferred: ScreenPath, background: Boolean, bridge: Boolean, onscreen: Boolean): List<String> =
        when (preferred) {
            ScreenPath.AUTO -> buildList {
                if (background) add("background")
                if (onscreen) add("accessibility")
            }

            ScreenPath.BACKGROUND -> if (background) listOf("background") else emptyList()
            ScreenPath.BRIDGE -> if (bridge) listOf("bridge") else emptyList()
            ScreenPath.ONSCREEN -> if (onscreen) listOf("accessibility") else emptyList()
        }
}

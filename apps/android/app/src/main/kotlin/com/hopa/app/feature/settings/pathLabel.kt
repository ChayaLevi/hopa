package com.hopa.app.feature.settings

/** The hub's one-line description of the screen path setting. */
fun pathLabel(value: String): String = when (value) {
    "background" -> "Background screen only"
    "onscreen" -> "This screen only"
    "bridge" -> "PC bridge only"
    else -> "Auto: hidden screen, bridge, this screen"
}

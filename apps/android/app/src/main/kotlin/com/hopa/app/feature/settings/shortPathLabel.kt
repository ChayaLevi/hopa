package com.hopa.app.feature.settings

/** A two-word label for the screen-path tile. */
fun shortPathLabel(value: String): String = when (value) {
    "background" -> "Hidden screen"
    "onscreen" -> "This screen"
    "bridge" -> "PC bridge"
    else -> "Auto"
}

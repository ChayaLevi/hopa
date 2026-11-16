package com.hopa.app.feature.settings

/** A two-word label for the tile. */
fun shortPathLabel(value: String): String = when (value) {
    "background" -> "Background"
    "onscreen" -> "This phone"
    "bridge" -> "Computer"
    else -> "Automatic"
}

package com.hopa.app.feature.settings

/** The hub's one-line description of where apps run. */
fun pathLabel(value: String): String = when (value) {
    "background" -> "Without showing"
    "onscreen" -> "On this phone"
    "bridge" -> "From my computer"
    else -> "Automatic"
}

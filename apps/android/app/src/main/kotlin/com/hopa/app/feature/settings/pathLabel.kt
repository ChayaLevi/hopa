package com.hopa.app.feature.settings

/** The hub's one-line description of where apps run. */
fun pathLabel(value: String): String = when (value) {
    "background" -> "Hidden screen"
    "onscreen" -> "Your screen"
    "bridge" -> "My computer"
    else -> "Hidden screen, then your screen"
}

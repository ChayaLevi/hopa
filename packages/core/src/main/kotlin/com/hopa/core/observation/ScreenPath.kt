package com.hopa.core.observation

/** How a run can reach the phone's screen, slowest to fastest by trust. */
enum class ScreenPath(val wire: String) {
    /** The phone's best mode: the background display when it is up, else accessibility. */
    AUTO("auto"),

    /** A hidden virtual display driven through a shell helper. */
    BACKGROUND("background"),

    /** The visible screen through the accessibility service. */
    ONSCREEN("onscreen"),

    /** A paired PC bridge over adb. */
    BRIDGE("bridge"),
    ;

    companion object {
        fun of(value: String): ScreenPath = entries.firstOrNull { it.wire == value.trim().lowercase() } ?: AUTO
    }
}

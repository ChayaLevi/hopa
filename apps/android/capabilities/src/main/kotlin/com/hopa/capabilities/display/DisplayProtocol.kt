package com.hopa.capabilities.display

/**
 * The words between the app and the shell helper. One line per command, one
 * line per reply, then raw bytes for a frame. Keeping it textual means the
 * protocol is testable without Android and readable in a log.
 */
class DisplayProtocol {
    fun display(width: Int, height: Int, dpi: Int): String = "display $width $height $dpi"

    fun launch(packageName: String): String = "launch $packageName"

    fun tap(x: Int, y: Int): String = "tap $x $y"

    fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, millis: Int): String =
        "swipe $fromX $fromY $toX $toY $millis"

    fun key(code: Int): String = "key $code"

    fun shot(): String = "shot"

    fun top(): String = "top"

    /** Close the apps this run started, so releasing the display cannot push them onto the phone. */
    fun finish(packages: List<String>): String = "finish ${packages.joinToString(",")}"

    fun release(): String = "release"

    /** `ok <payload>` or `err <message>`; the payload may be empty. */
    fun parse(reply: String): Pair<Boolean, String> {
        val line = reply.trim()
        return when {
            line.startsWith("ok") -> true to line.removePrefix("ok").trim()
            line.startsWith("err") -> false to line.removePrefix("err").trim()
            else -> false to line.ifBlank { "empty reply" }
        }
    }
}

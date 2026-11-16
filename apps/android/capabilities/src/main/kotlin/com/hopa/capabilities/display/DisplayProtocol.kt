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

    /** List the AppFunctions an app exposes (Android 16+). */
    fun appFunctions(packageName: String): String = "appfn $packageName"

    /** Call one AppFunction; [parameters] is compact JSON with no spaces. */
    fun appFunctionCall(packageName: String, function: String, parameters: String): String =
        "appfn-call $packageName $function $parameters"

    fun shot(): String = "shot"

    fun top(): String = "top"

    /** A run on the hidden display must not be heard on the phone. */
    fun mute(packageName: String): String = "mute $packageName"

    fun unmute(packageName: String): String = "unmute $packageName"

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

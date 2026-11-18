package com.hopa.capabilities.display

import java.io.File

/**
 * Starts and inspects apps on a virtual display with the shell's own tools. Going
 * through `cmd package` and `am` avoids hidden framework calls and reports the
 * system's own error text when an app refuses to start there.
 */
class DisplayLauncher(
    private val displayId: Int,
) {
    fun launch(packageName: String): String {
        val component = run("cmd package resolve-activity --brief $packageName")
            .lineSequence()
            .map { it.trim() }
            .lastOrNull { it.contains('/') }
            ?: return "err no launchable activity for $packageName"
        val start = run("am start --display $displayId -n $component")
        // "Warning: Activity not started, its current task has been brought to
        // the front" means the app did not open on this display, even though am
        // exits 0. Reporting "ok" there left the loop reading an empty screen and
        // guessing, so any refusal counts as an error now.
        val refusal = start.lineSequence()
            .map { it.trim() }
            .firstOrNull {
                it.startsWith("Error") ||
                    it.contains("Exception") ||
                    it.contains("Warning: Activity not started")
            }
        return if (refusal != null) "err $refusal" else "ok $component"
    }

    fun topPackage(): String {
        val dump = run("dumpsys window displays")
        val focus = dump.lineSequence()
            .firstOrNull { it.contains("mCurrentFocus") || it.contains("mFocusedApp") }
            ?: return ""
        val match = Regex("""([a-zA-Z][\w.]*)/[\w.$]+""").find(focus) ?: return ""
        return match.groupValues[1]
    }

    private fun run(command: String): String = runCatching {
        val process = ProcessBuilder("sh", "-c", command)
            .redirectErrorStream(true)
            .start()
        val text = process.inputStream.bufferedReader().readText()
        process.waitFor()
        text
    }.getOrDefault("")

    private fun File.text(): String = readText()
}

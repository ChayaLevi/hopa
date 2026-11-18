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
        // -W waits for the launch to report, so "ok" means the activity really
        // started on this display; without it a slow or refused launch looked
        // like success and the next read found an empty screen.
        val start = run("am start -W --display $displayId -n $component")
        // "Warning: Activity not started, its current task has been brought to
        // the front" means the app did not open here, even though am exits 0.
        val refusal = start.lineSequence()
            .map { it.trim() }
            .firstOrNull {
                it.startsWith("Error") ||
                    it.contains("Exception") ||
                    it.contains("Warning: Activity not started")
            }
        if (refusal != null) {
            return "err $refusal"
        }
        val status = start.lineSequence()
            .firstOrNull { it.startsWith("Status:") }
            ?.substringAfter("Status:")
            ?.trim()
        return if (status != null && !status.equals("ok", ignoreCase = true)) {
            "err launch status $status"
        } else {
            "ok $component"
        }
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

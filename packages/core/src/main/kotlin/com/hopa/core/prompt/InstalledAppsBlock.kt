package com.hopa.core.prompt

import com.hopa.core.ports.InstalledApp

/**
 * The apps this phone can actually open. The catalog names apps; this lists the
 * package that exists on the device, which is the only thing ui.launch accepts.
 * Without it the model had no package for any app and the router told it not to
 * guess, so an app could be named but never opened.
 */
class InstalledAppsBlock(
    private val limit: Int = 100,
) {
    fun text(apps: List<InstalledApp>): String {
        if (apps.isEmpty()) {
            return "APPS THIS CLIENT CAN OPEN: none could be listed.\n" +
                "Do not guess a package name; ask the person to open the app."
        }
        val lines = mutableListOf("APPS THIS CLIENT CAN OPEN (label - target):")
        apps.asSequence()
            .filter { it.label.isNotBlank() && it.packageName.isNotBlank() }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .take(limit)
            .forEach { lines += "${it.label} - ${it.packageName}" }
        lines += ""
        lines += "Open one of these with ui.launch and its target. " +
            "Never invent a target and never launch the same app twice."
        return lines.joinToString("\n")
    }
}

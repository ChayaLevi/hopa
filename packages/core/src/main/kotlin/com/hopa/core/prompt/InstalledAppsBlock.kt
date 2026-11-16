package com.hopa.core.prompt

import com.hopa.core.ports.InstalledApp

/**
 * Apps on this device that the catalog does not describe. The catalog block
 * already names the ones HOPA knows, with their rules; repeating them here only
 * spends context, so this list is the remainder: things a person installed that
 * HOPA has no opinion about.
 */
class InstalledAppsBlock(
    private val limit: Int = 100,
) {
    fun text(apps: List<InstalledApp>, known: Set<String> = emptySet()): String {
        if (apps.isEmpty()) {
            return "APPS THIS CLIENT CAN OPEN: none could be listed.\n" +
                "Do not guess a package name; ask the person to open the app."
        }
        val rest = apps.asSequence()
            .filter { it.label.isNotBlank() && it.packageName.isNotBlank() }
            .filter { it.packageName.lowercase() !in known }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .take(limit)
            .toList()
        if (rest.isEmpty()) {
            return ""
        }
        val lines = mutableListOf("OTHER APPS ON THIS PHONE (label - target):")
        rest.forEach { lines += "${it.label} - ${it.packageName}" }
        lines += ""
        lines += "These have no catalog entry, so no rules come with them. " +
            "Prefer the apps above; ask before doing something irreversible in one of these."
        return lines.joinToString("\n")
    }
}

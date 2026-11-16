package com.hopa.core.prompt

import com.hopa.core.router.RouteTarget

/**
 * The catalog as the model reads it: one line per app this client can open,
 * with the guardrails the catalog keeps for it. The guardrails matter as much as
 * the name: "a payment always asks" is the sentence that stops a tap.
 */
object AppCatalogBlock {
    fun text(targets: List<RouteTarget>): String {
        val apps = targets
            .filter { it.kind == "app" && it.bundle.isNotBlank() }
            .sortedBy { it.display.lowercase() }
        val sites = targets
            .filter { it.kind == "site" && it.domains.isNotEmpty() }
            .sortedBy { it.display.lowercase() }
        if (apps.isEmpty() && sites.isEmpty()) {
            return ""
        }
        val lines = mutableListOf("APPS HOPA KNOWS (name: target - what it is for | rules):")
        for (app in apps) {
            val purpose = app.purpose.takeIf { it.isNotEmpty() }?.let { " - $it" }.orEmpty()
            val rules = app.actions.takeIf { it.isNotEmpty() }
                ?.let { " | " + it.joinToString("; ") }
                .orEmpty()
            lines += "${app.display}: ${app.bundle}$purpose$rules"
        }
        for (site in sites) {
            val domain = site.domains.firstOrNull() ?: continue
            val purpose = site.purpose.takeIf { it.isNotEmpty() }?.let { " - $it" }.orEmpty()
            lines += "${site.display} (site: $domain)$purpose"
        }
        lines += ""
        lines += "Open an app with {\"kind\":\"ui.launch\",\"app\":\"<target>\"}. " +
            "Follow the rules on that line. Never invent a target."
        return lines.joinToString("\n")
    }
}

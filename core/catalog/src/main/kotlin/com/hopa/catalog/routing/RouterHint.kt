package com.hopa.catalog.routing

object RouterHint {
    fun text(match: RouteMatch, webAvailable: Boolean = true): String {
        val target = match.target ?: return ""
        if (target.kind == "site") {
            val domain = target.domains.firstOrNull() ?: return ""
            return if (webAvailable) {
                "ROUTER SUGGESTION: this is a ${target.display} task. " +
                    "Open https://$domain with {\"kind\":\"web.navigate\",\"url\":\"https://$domain\"}, " +
                    "then read the page and use its element refs."
            } else {
                "ROUTER SUGGESTION: this looks like a ${target.display} task, but this " +
                    "client has no web capability. If the catalog lists an app for it, use " +
                    "the app; otherwise say plainly that it cannot be done here."
            }
        }
        if (target.bundle.isEmpty()) {
            return "ROUTER SUGGESTION: this looks like a ${target.display} task, " +
                "but no launch target is verified for it on this device. Do not guess one; " +
                "say what is missing and offer the web channel if one exists."
        }
        return "ROUTER SUGGESTION: this is a ${target.display} task. " +
            "Start with {\"kind\":\"ui.launch\",\"app\":\"${target.bundle}\"}, " +
            "then act on the ELEMENTS of the new screen."
    }
}

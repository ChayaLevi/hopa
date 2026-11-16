package com.hopa.core.router

import com.hopa.catalog.APP_CATALOG
import com.hopa.catalog.CatalogApp
import com.hopa.catalog.SITE_CATALOG
import com.hopa.core.ports.InstalledApp

/**
 * The catalog is one list for every platform; this is where a client asks for
 * the launch target that belongs to it. A platform with no mapping gets an
 * empty target, which the router hint reports instead of inventing one.
 */
object CatalogRoutes {
    fun targets(installed: List<InstalledApp> = emptyList(), platform: String = ""): List<RouteTarget> {
        val apps = APP_CATALOG.map { app ->
            RouteTarget(
                name = app.id,
                display = app.display,
                kind = "app",
                bundle = launchTarget(app, platform, installed),
                aliases = app.aliases,
                purpose = app.purpose,
                actions = app.actions,
            )
        }
        val sites = SITE_CATALOG.map { site ->
            RouteTarget(
                name = site.id,
                display = site.display,
                kind = "site",
                domains = site.domains,
                aliases = site.aliases,
                purpose = site.purpose,
                actions = site.actions,
            )
        }
        return apps + sites
    }

    private fun launchTarget(app: CatalogApp, platform: String, installed: List<InstalledApp>): String {
        val target = when (platform.lowercase()) {
            "android" -> app.android
            "windows" -> app.windows
            "harmonyos", "harmony" -> app.harmonyos
            else -> return ""
        }
        if (target.id.isBlank()) {
            return ""
        }
        if (!platform.equals("android", ignoreCase = true)) {
            return target.id
        }
        // On a phone the installed list is the authority: an unconfirmed package
        // is only used when it is really there, otherwise a label match decides.
        val present = installed.any { it.packageName.equals(target.id, ignoreCase = true) }
        if (target.verified || present) {
            return target.id
        }
        return installedPackage(app.display, app.aliases, installed)
    }

    /** The phone's own package for a catalog name; empty when the app is not installed. */
    private fun installedPackage(display: String, aliases: List<String>, installed: List<InstalledApp>): String {
        if (installed.isEmpty()) {
            return ""
        }
        val names = (listOf(display) + aliases).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        return installed.firstOrNull { app ->
            val label = app.label.trim().lowercase()
            label.isNotEmpty() && names.contains(label)
        }?.packageName.orEmpty()
    }
}

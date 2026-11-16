package com.hopa.core.router

import com.hopa.catalog.APP_CATALOG
import com.hopa.catalog.CatalogApp
import com.hopa.catalog.SITE_CATALOG
import com.hopa.core.ports.InstalledApp

/**
 * The catalog is one list for every platform; this is where a client asks for
 * the launch target that belongs to it. A platform with no mapping gets an
 * empty target, which the router hint reports instead of inventing one.
 *
 * The status says what a surface can honestly offer: `ready` when the app opens
 * on this device, `unconfirmed` when the catalog names a target the device does
 * not have, `none` when there is no target for this platform at all.
 */
object CatalogRoutes {
    fun targets(installed: List<InstalledApp> = emptyList(), platform: String = ""): List<RouteTarget> {
        val apps = APP_CATALOG.map { app ->
            val launch = launchTarget(app, platform, installed)
            RouteTarget(
                name = app.id,
                display = app.display,
                kind = "app",
                bundle = launch.first,
                aliases = app.aliases,
                purpose = app.purpose,
                actions = app.actions,
                status = launch.second,
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
                status = if (site.domains.isEmpty()) "none" else "ready",
            )
        }
        return apps + sites
    }

    private fun launchTarget(app: CatalogApp, platform: String, installed: List<InstalledApp>): Pair<String, String> {
        val target = when (platform.lowercase()) {
            "android" -> app.android
            "windows" -> app.windows
            "harmonyos", "harmony" -> app.harmonyos
            else -> return "" to "none"
        }
        if (target.id.isBlank()) {
            return "" to "none"
        }
        if (!platform.equals("android", ignoreCase = true)) {
            return target.id to "ready"
        }
        // On a phone the installed list is the authority: an unconfirmed package
        // is only used when it is really there, otherwise a label match decides.
        val present = installed.any { it.packageName.equals(target.id, ignoreCase = true) }
        if (target.verified || present) {
            return target.id to "ready"
        }
        val byLabel = installedPackage(app.display, app.aliases, installed)
        return if (byLabel.isEmpty()) "" to "unconfirmed" else byLabel to "ready"
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

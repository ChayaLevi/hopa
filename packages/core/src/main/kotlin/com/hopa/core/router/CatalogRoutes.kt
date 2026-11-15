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
    fun targets(
        installed: List<InstalledApp> = emptyList(),
        platform: String = "",
    ): List<RouteTarget> {
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

    private fun launchTarget(
        app: CatalogApp,
        platform: String,
        installed: List<InstalledApp>,
    ): String = when (platform.lowercase()) {
        "android" -> app.android.id.ifBlank {
            // The catalog may lag behind the phone; a matching label is a last resort.
            installedPackage(app.display, app.aliases, installed)
        }

        "harmonyos", "harmony" -> app.harmonyos.id
        "windows" -> app.windows.id
        else -> ""
    }

    /** The phone's own package for a catalog name; empty when the app is not installed. */
    private fun installedPackage(
        display: String,
        aliases: List<String>,
        installed: List<InstalledApp>,
    ): String {
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

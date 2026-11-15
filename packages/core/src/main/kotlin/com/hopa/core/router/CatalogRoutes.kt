package com.hopa.core.router

import com.hopa.catalog.APP_CATALOG
import com.hopa.catalog.SITE_CATALOG
import com.hopa.core.ports.InstalledApp

object CatalogRoutes {
    fun targets(installed: List<InstalledApp> = emptyList()): List<RouteTarget> {
        val apps = APP_CATALOG.map { app ->
            RouteTarget(
                name = app.id,
                display = app.display,
                kind = "app",
                bundle = app.bundle.ifBlank { installedPackage(app.display, app.aliases, installed) },
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

package com.hopa.app.feature.launchpad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.hopa.app.di.AppContainer
import com.hopa.engine.routing.CatalogRoutes
import com.hopa.engine.routing.RouteTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The catalog, resolved for this phone. Nothing here lists apps the catalog does
 * not describe, and a row that cannot open says why on its card instead of doing
 * nothing.
 */
@Composable
fun LaunchpadRoute(
    container: AppContainer,
    onBack: () -> Unit,
    section: String = "apps",
    onOpenApp: (String) -> Unit,
    onOpenSite: (String) -> Unit,
) {
    var targets by remember { mutableStateOf(emptyList<RouteTarget>()) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val installed = withContext(Dispatchers.IO) {
            runCatching { container.packages.installed() }.getOrDefault(emptyList())
        }
        targets = runCatching { CatalogRoutes.targets(installed, "android", container.knowledge) }
            .getOrElse {
                status = "Could not read the catalog: ${it.message ?: "unknown error"}"
                emptyList()
            }
            // Only what this phone can attempt: an app whose catalog entry has no
            // Android target is a row that can never open, so it is not shown.
            .filter { target -> target.kind == "site" || target.status != "none" }
    }

    LaunchpadScreen(
        apps = targets.filter { it.kind == "app" },
        sites = targets.filter { it.kind == "site" },
        status = status,
        initialSection = if (section == "sites") 1 else 0,
        onBack = onBack,
        onOpenApp = { target -> onOpenApp(target.name) },
        onOpenSite = { site ->
            val domain = site.domains.firstOrNull().orEmpty()
            if (domain.isNotBlank()) {
                onOpenSite("https://$domain")
            }
        },
    )
}

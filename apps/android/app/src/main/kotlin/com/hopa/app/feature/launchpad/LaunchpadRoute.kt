package com.hopa.app.feature.launchpad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.hopa.app.di.AppContainer
import com.hopa.core.router.CatalogRoutes
import com.hopa.core.router.RouteTarget
import kotlinx.coroutines.launch

/**
 * The catalog, resolved for this phone. Nothing here lists apps the catalog does
 * not describe, and a row that cannot open says why instead of doing nothing.
 */
@Composable
fun LaunchpadRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenSite: (String) -> Unit,
) {
    var targets by remember { mutableStateOf(emptyList<RouteTarget>()) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val installed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { container.packages.installed() }.getOrDefault(emptyList())
        }
        targets = runCatching { CatalogRoutes.targets(installed, "android") }
            .getOrElse {
                status = "Could not read the catalog: ${it.message ?: "unknown error"}"
                emptyList()
            }
    }

    LaunchpadScreen(
        apps = targets.filter { it.kind == "app" },
        sites = targets.filter { it.kind == "site" },
        status = status,
        onBack = onBack,
        onLaunch = { target ->
            scope.launch {
                status = runCatching {
                    container.packages.launch(target.bundle)
                    "Opened ${target.display}."
                }.getOrElse {
                    "Couldn't open ${target.display}: ${it.message ?: "no launch intent"}"
                }
            }
        },
        onOpenSite = { site ->
            val domain = site.domains.firstOrNull().orEmpty()
            if (domain.isNotBlank()) {
                onOpenSite("https://$domain")
            }
        },
    )
}

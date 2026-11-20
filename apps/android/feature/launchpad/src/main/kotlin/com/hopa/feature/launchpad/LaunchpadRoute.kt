package com.hopa.feature.launchpad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The catalog, resolved for this phone. Nothing here lists apps the catalog does
 * not describe, and a row that cannot open says why on its card instead of doing
 * nothing.
 */
@Composable
fun LaunchpadRoute(
    onBack: () -> Unit,
    section: String = "apps",
    onOpenApp: (String) -> Unit,
    onOpenSite: (String) -> Unit,
) {
    val viewModel: LaunchpadViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchpadScreen(
        apps = state.targets.filter { it.kind == "app" },
        sites = state.targets.filter { it.kind == "site" },
        status = state.status,
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

package com.hopa.app.feature.runningapps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hopa.app.di.AppContainer
import com.hopa.app.feature.settings.SettingsViewModel
import com.hopa.app.feature.settings.SettingsViewModelFactory
import com.hopa.core.router.CatalogRoutes
import com.hopa.core.router.RouteTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The running-apps page shares the settings store, and lists the apps it can open. */
@Composable
fun RunningAppsRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenScreenAccess: () -> Unit,
    onOpenComputer: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pairing by container.pairingStatus.collectAsStateWithLifecycle()
    var apps by remember { mutableStateOf(emptyList<RouteTarget>()) }

    LaunchedEffect(Unit) {
        val installed = withContext(Dispatchers.IO) {
            runCatching { container.packages.installed() }.getOrDefault(emptyList())
        }
        apps = runCatching { CatalogRoutes.targets(installed, "android") }
            .getOrDefault(emptyList())
            .filter { it.kind == "app" && it.status == "ready" }
            .sortedBy { it.display.lowercase() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3_000)
            viewModel.refreshBackground()
        }
    }

    RunningAppsScreen(
        state = state,
        apps = apps,
        pairingStatus = pairing,
        onBack = onBack,
        onScreenPath = viewModel::setScreenPath,
        onStartBackgroundHelper = viewModel::startBackgroundHelper,
        onPairWireless = viewModel::pairWireless,
        onPairWirelessFromScreen = viewModel::pairWirelessFromScreen,
        onPairWirelessWaiting = viewModel::pairWirelessWaiting,
        onRunCheck = viewModel::runScreenCheck,
        onPlanFirst = viewModel::setPlanFirst,
        onVision = viewModel::setVision,
        onReplaySkills = viewModel::setReplaySkills,
        onOpenApp = onOpenApp,
        onOpenAllApps = onOpenAllApps,
        onOpenScreenAccess = onOpenScreenAccess,
        onOpenComputer = onOpenComputer,
    )
}

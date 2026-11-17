package com.hopa.app.feature.runningapps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hopa.app.di.AppContainer
import com.hopa.app.feature.settings.SettingsViewModel
import com.hopa.app.feature.settings.SettingsViewModelFactory

/** The Act hub: where HOPA works, and the ways into apps, setup and preferences. */
@Composable
fun RunningAppsRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenSites: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenRunPrefs: () -> Unit,
    onOpenScreenAccess: () -> Unit,
    onOpenComputer: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pairing by container.pairingStatus.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3_000)
            viewModel.refreshBackground()
        }
    }

    RunningAppsScreen(
        state = state,
        pairingStatus = pairing,
        onBack = onBack,
        onScreenPath = viewModel::setScreenPath,
        onStartBackgroundHelper = viewModel::startBackgroundHelper,
        onPairWireless = viewModel::pairWireless,
        onPairWirelessFromScreen = viewModel::pairWirelessFromScreen,
        onPairWirelessWaiting = viewModel::pairWirelessWaiting,
        onRunCheck = viewModel::runScreenCheck,
        onOpenSetup = onOpenSetup,
        onOpenRunPrefs = onOpenRunPrefs,
        onOpenAllApps = onOpenAllApps,
        onOpenSites = onOpenSites,
        onOpenScreenAccess = onOpenScreenAccess,
        onOpenComputer = onOpenComputer,
    )
}

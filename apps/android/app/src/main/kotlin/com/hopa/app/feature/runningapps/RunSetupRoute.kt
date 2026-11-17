package com.hopa.app.feature.runningapps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hopa.app.di.AppContainer
import com.hopa.app.feature.settings.SettingsViewModel
import com.hopa.app.feature.settings.SettingsViewModelFactory

@Composable
fun RunSetupRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenScreenAccess: () -> Unit,
    onOpenComputer: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(3_000)
            viewModel.refreshBackground()
        }
    }

    RunSetupScreen(
        state = state,
        onBack = onBack,
        onOpenScreenAccess = onOpenScreenAccess,
        onStartBackgroundHelper = viewModel::startBackgroundHelper,
        onOpenComputer = onOpenComputer,
        onRunCheck = viewModel::runScreenCheck,
        onPairWireless = viewModel::pairWireless,
        onPairWirelessFromScreen = viewModel::pairWirelessFromScreen,
        onPairWirelessWaiting = viewModel::pairWirelessWaiting,
    )
}

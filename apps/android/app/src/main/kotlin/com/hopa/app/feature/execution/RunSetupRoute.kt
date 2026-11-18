package com.hopa.app.feature.execution

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val pairing by container.pairingStatus.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // The supervisor owns the helper's state and publishes it; the page reads it
    // once when it opens and follows the flow after that.
    LaunchedEffect(Unit) { viewModel.refreshBackground() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
                viewModel.refreshBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    RunSetupScreen(
        state = state,
        pairingStatus = pairing,
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

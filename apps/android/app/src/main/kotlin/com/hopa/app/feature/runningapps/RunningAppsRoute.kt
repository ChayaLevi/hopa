package com.hopa.app.feature.runningapps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hopa.app.di.AppContainer
import com.hopa.app.feature.settings.SettingsViewModel
import com.hopa.app.feature.settings.SettingsViewModelFactory

/** The running-apps page shares the model settings' state: one settings store. */
@Composable
fun RunningAppsRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenLaunchpad: () -> Unit,
    onOpenScreenAccess: () -> Unit,
    onOpenComputer: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    RunningAppsScreen(
        state = state,
        onBack = onBack,
        onScreenPath = viewModel::setScreenPath,
        onStartBackgroundHelper = viewModel::startBackgroundHelper,
        onPairWireless = viewModel::pairWireless,
        onRunCheck = viewModel::runScreenCheck,
        onPlanFirst = viewModel::setPlanFirst,
        onVision = viewModel::setVision,
        onReplaySkills = viewModel::setReplaySkills,
        onOpenLaunchpad = onOpenLaunchpad,
        onOpenScreenAccess = onOpenScreenAccess,
        onOpenComputer = onOpenComputer,
    )
}

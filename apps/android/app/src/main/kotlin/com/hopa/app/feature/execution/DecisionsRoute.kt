package com.hopa.app.feature.execution

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hopa.feature.execution.DecisionsScreen
import com.hopa.feature.settings.SettingsViewModel

/**
 * The Decisions page, reached from How HOPA acts.
 *
 * It reuses the settings view model because the choices are settings; the page
 * lives under Act because it decides who acts, not how a chat looks.
 */
@Composable
fun DecisionsRoute(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    DecisionsScreen(
        state = state,
        onBack = onBack,
        onMode = viewModel::setDecisionMode,
        onShadow = viewModel::setShadowDecisions,
        onInstantCommands = viewModel::setInstantCommands,
    )
}

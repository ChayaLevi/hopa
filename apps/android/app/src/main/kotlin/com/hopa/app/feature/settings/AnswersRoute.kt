package com.hopa.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hopa.feature.settings.SettingsModelScreen
import com.hopa.feature.settings.SettingsViewModel

/**
 * The setup behind an answer: endpoint, key, model name, downloads.
 *
 * It is reachable from How HOPA acts, where the model itself is chosen, so the
 * page carries no provider switch of its own.
 */
@Composable
fun AnswersRoute(onBack: () -> Unit) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsModelScreen(
        state = state,
        onBack = onBack,
        onSave = viewModel::save,
        onTestModel = viewModel::testModel,
        onDeviceModels = { OnDeviceModels() },
    )
}

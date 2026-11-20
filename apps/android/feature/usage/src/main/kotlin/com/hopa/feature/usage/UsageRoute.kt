package com.hopa.feature.usage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun UsageRoute(
    container: UsageDependencies,
    onBack: () -> Unit,
) {
    val viewModel: UsageViewModel = viewModel(factory = UsageViewModelFactory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    UsageScreen(state = state, onBack = onBack)
}

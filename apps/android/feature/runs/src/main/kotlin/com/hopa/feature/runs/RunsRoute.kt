package com.hopa.feature.runs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun RunsRoute(
    onBack: () -> Unit,
) {
    val viewModel: RunsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    RunsScreen(
        records = state.records,
        onBack = onBack,
        onVerify = viewModel::verify,
        onClear = viewModel::clearAll,
    )
}

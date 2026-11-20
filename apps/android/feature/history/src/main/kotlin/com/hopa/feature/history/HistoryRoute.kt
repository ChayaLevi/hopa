package com.hopa.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Finished turns, with the surface filter and one clear action. */
@Composable
fun HistoryRoute(
    onBack: () -> Unit,
) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    HistoryScreen(
        rows = state.rows,
        selected = state.filter,
        onFilter = viewModel::setFilter,
        onBack = onBack,
        onClear = viewModel::clearAll,
    )
}

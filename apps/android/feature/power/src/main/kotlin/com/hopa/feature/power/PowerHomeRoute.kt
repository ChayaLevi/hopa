package com.hopa.feature.power

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PowerHomeRoute(
    active: String,
    onSelectTab: (String) -> Unit,
    onRun: (String) -> Unit,
) {
    val viewModel: PowerHomeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    PowerHomeScreen(
        recent = state.recent,
        active = active,
        onSelectTab = onSelectTab,
        onRun = onRun,
    )
}

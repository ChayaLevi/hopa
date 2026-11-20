package com.hopa.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hopa.feature.settings.SelfCheckScreen

@Composable
fun SelfCheckRoute(
    onBack: () -> Unit,
) {
    val viewModel: SelfCheckViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    SelfCheckScreen(
        state = state,
        onBack = onBack,
        onRetry = viewModel::retry,
    )
}

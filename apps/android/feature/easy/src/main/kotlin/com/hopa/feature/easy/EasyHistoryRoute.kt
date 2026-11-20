package com.hopa.feature.easy

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun EasyHistoryRoute(
    active: String,
    onSelectTab: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val viewModel: EasyHistoryViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    EasyHistoryScreen(
        conversations = state.conversations,
        active = active,
        onSelectTab = onSelectTab,
        onOpen = onOpen,
    )
}

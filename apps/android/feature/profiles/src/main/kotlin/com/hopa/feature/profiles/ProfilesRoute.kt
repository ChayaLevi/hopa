package com.hopa.feature.profiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@Composable
fun ProfilesRoute(
    onBack: () -> Unit,
) {
    val viewModel: ProfilesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    ProfilesScreen(
        selected = state.selected,
        onBack = onBack,
        onSelect = { profile ->
            viewModel.select(profile)
            scope.launch {
                viewModel.save(profile)
                onBack()
            }
        },
    )
}

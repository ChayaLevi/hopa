package com.hopa.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Paths that worked before, and the goal that brings each one back. */
@Composable
fun RecipesRoute(
    onBack: () -> Unit,
) {
    val viewModel: RecipesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    RecipesScreen(
        recipes = state.recipes,
        names = state.names,
        onBack = onBack,
        onDelete = viewModel::delete,
    )
}

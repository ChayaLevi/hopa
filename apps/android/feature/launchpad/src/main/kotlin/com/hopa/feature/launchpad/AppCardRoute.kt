package com.hopa.feature.launchpad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** One catalog entry for this phone: status, surface, rules, and two actions. */
@Composable
fun AppCardRoute(
    name: String,
    onBack: () -> Unit,
    onOpenScreenAccess: () -> Unit,
) {
    val viewModel: AppCardViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(name) {
        viewModel.refresh(name)
    }

    AppCardScreen(
        target = state.target,
        surface = state.surface,
        screenAccess = state.screenAccess,
        trusted = state.trusted,
        status = state.status,
        functions = state.functions,
        onBack = onBack,
        onOpen = viewModel::open,
        onInstall = viewModel::install,
        onOpenScreenAccess = onOpenScreenAccess,
        onTrust = viewModel::setTrusted,
    )
}

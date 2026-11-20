package com.hopa.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hopa.capabilities.accessibility.state.AccessibilityState
import com.hopa.feature.settings.AccessibilityScreen

@Composable
fun AccessibilityRoute(
    onBack: () -> Unit,
) {
    val viewModel: AccessibilityViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val access by AccessibilityState.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    // Turning the service on happens in Android settings, which sends no event
    // to this app; coming back to the foreground is the moment to ask again.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAccessibility()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AccessibilityScreen(
        allowed = state.allowed,
        serviceEnabled = access.enabled,
        connected = access.connected,
        installedAt = state.installedAt,
        onToggle = viewModel::setAllowed,
        onOpenSettings = viewModel::openSettings,
        onBack = onBack,
    )
}

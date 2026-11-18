package com.hopa.app.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hopa.app.di.AppContainer
import com.hopa.capabilities.accessibility.AccessibilityState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun AccessibilityRoute(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(true) }
    val access by AccessibilityState.state.collectAsStateWithLifecycle()
    val serviceEnabled = access.enabled
    val connected = access.connected

    LaunchedEffect(Unit) {
        allowed = container.settings.settings.first().accessibilityAllowed
        AccessibilityState.refresh(context)
    }
    // Turning the service on happens in Android settings, which sends no event
    // to this app; coming back to the foreground is the moment to ask again.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                AccessibilityState.refresh(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val installedAt = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val install = java.text.SimpleDateFormat(
                "yyyy-MM-dd HH:mm",
                java.util.Locale.getDefault(),
            ).format(java.util.Date(info.lastUpdateTime))
            "${info.versionName} (installed $install)"

        }.getOrDefault("")
    }

    AccessibilityScreen(
        allowed = allowed,
        serviceEnabled = serviceEnabled,
        connected = connected,
        installedAt = installedAt,
        onToggle = { value ->
            allowed = value
            scope.launch {
                container.settings.update { it.copy(accessibilityAllowed = value) }
            }
        },
        onOpenSettings = { AccessibilityState.openSettings(context) },
        onBack = onBack,
    )
}

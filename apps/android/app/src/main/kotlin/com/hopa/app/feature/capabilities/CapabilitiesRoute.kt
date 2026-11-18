package com.hopa.app.feature.capabilities

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hopa.app.di.AppContainer
import com.hopa.capabilities.accessibility.AccessibilityState
import kotlinx.coroutines.flow.first

@Composable
fun CapabilitiesRoute(
    container: AppContainer,
    activeTab: String,
    onSelectTab: (String) -> Unit,
    onOpenRunningApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRuns: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenBrowser: () -> Unit,
) {
    var records by remember { mutableStateOf(0) }
    var facts by remember { mutableStateOf(0) }
    var skills by remember { mutableStateOf(0) }
    var allowed by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val access by AccessibilityState.state.collectAsStateWithLifecycle()
    val accessibilityEnabled = allowed && access.ready

    LaunchedEffect(Unit) {
        records = container.recorder.all().size
        facts = container.memory.all().size
        skills = container.skills.all().size
        allowed = container.settings.settings.first().accessibilityAllowed
        AccessibilityState.refresh(container.context)
    }
    // The tab is the place a person checks after changing something in Android
    // settings, which sends this app no event; ask again when they come back.
    LaunchedEffect(activeTab) {
        if (activeTab == "capabilities") {
            AccessibilityState.refresh(container.context)
        }
    }

    CapabilitiesScreen(
        records = records,
        facts = facts,
        skills = skills,
        accessibilityEnabled = accessibilityEnabled,
        activeTab = activeTab,
        onSelectTab = onSelectTab,
        onOpenRunningApps = onOpenRunningApps,
        onOpenSettings = onOpenSettings,
        onOpenRuns = onOpenRuns,
        onOpenUsage = onOpenUsage,
        onOpenMemory = onOpenMemory,
        onOpenSkills = onOpenSkills,
        onOpenBrowser = onOpenBrowser,
        onUnavailable = { feature ->
            Toast.makeText(
                context,
                "$feature is not on Android yet.",
                Toast.LENGTH_SHORT,
            ).show()
        },
    )
}

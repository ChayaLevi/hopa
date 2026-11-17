package com.hopa.app.feature.capabilities

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hopa.app.di.AppContainer
import com.hopa.capabilities.accessibility.AccessibilityState
import com.hopa.capabilities.accessibility.HopaAccessibilityService
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
    var accessibilityEnabled by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        records = container.recorder.all().size
        facts = container.memory.all().size
        skills = container.skills.all().size
        accessibilityEnabled = AccessibilityState.isEnabled(container.context) &&
            HopaAccessibilityService.instance != null &&
            container.settings.settings.first().accessibilityAllowed
    }
    // The tab is the place a person checks, so the status is re-read when they
    // come back to it instead of showing what was true when the app started.
    LaunchedEffect(activeTab) {
        if (activeTab == "capabilities") {
            accessibilityEnabled = AccessibilityState.isEnabled(container.context) &&
                HopaAccessibilityService.instance != null &&
                container.settings.settings.first().accessibilityAllowed
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

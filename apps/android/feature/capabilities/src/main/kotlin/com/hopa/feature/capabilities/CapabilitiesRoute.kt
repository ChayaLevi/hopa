package com.hopa.feature.capabilities

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hopa.capabilities.accessibility.state.AccessibilityState

@Composable
fun CapabilitiesRoute(
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
    val viewModel: CapabilitiesViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val access by AccessibilityState.state.collectAsStateWithLifecycle()
    val accessibilityEnabled = state.allowed && access.ready

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }
    // The tab is the place a person checks after changing something in Android
    // settings, which sends this app no event; ask again when they come back.
    LaunchedEffect(activeTab) {
        if (activeTab == "capabilities") {
            viewModel.refreshAccessibility()
        }
    }

    CapabilitiesScreen(
        records = state.records,
        facts = state.facts,
        skills = state.skills,
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

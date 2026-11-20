package com.hopa.app.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hopa.app.di.AppContainer
import com.hopa.feature.history.ActivityRoute
import com.hopa.feature.capabilities.CapabilitiesRoute
import com.hopa.app.feature.chat.ChatRoute
import com.hopa.feature.easy.EasyHistoryRoute
import com.hopa.feature.power.PowerHomeRoute
import com.hopa.stores.AppSettings

/**
 * The three top-level pages from Index.ets. The tab state lives here, not in
 * the navigation graph, so switching tabs never stacks a back entry.
 *
 * The Easy profile gets its own bar (Talk / History / Settings), as on Harmony.
 */
@Composable
fun HomeShell(
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onOpenRuns: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenRecipes: () -> Unit = {},
    onOpenMemory: () -> Unit,
    onOpenSkills: () -> Unit = onOpenMemory,
    onOpenRunningApps: () -> Unit,
    onOpenActMode: () -> Unit,
    onOpenBrowser: () -> Unit,
    initialGoal: String = "",
) {
    val settings by container.settings.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    val shared by container.pendingAttachment.collectAsStateWithLifecycle()
    val sharedNotice by container.pendingNotice.collectAsStateWithLifecycle()
    val incoming = shared != null || sharedNotice != null
    if (settings.profile == "easy") {
        EasyShell(
            container = container,
            onOpenSettings = onOpenSettings,
            onOpenActMode = onOpenActMode,
            initialGoal = initialGoal,
            shared = incoming,
        )
        return
    }
    if (settings.profile == "power") {
        PowerShell(
            container = container,
            onOpenSettings = onOpenSettings,
            onOpenRuns = onOpenRuns,
            onOpenUsage = onOpenUsage,
            onOpenHistory = onOpenHistory,
            onOpenRecipes = onOpenRecipes,
            onOpenMemory = onOpenMemory,
            onOpenSkills = onOpenSkills,
            onOpenRunningApps = onOpenRunningApps,
            onOpenActMode = onOpenActMode,
            onOpenBrowser = onOpenBrowser,
            initialGoal = initialGoal,
            shared = incoming,
        )
        return
    }

    var tab by rememberSaveable { mutableStateOf("chat") }
    androidx.compose.runtime.LaunchedEffect(shared, sharedNotice) {
        if (shared != null || sharedNotice != null) {
            tab = "chat"
        }
    }
    val selectTab: (String) -> Unit = { key ->
        if (key == "chat" || key == "activity" || key == "capabilities") {
            tab = key
        }
    }

    when (tab) {
        "activity" -> ActivityRoute(
            activeTab = tab,
            onSelectTab = selectTab,
            onOpenRuns = onOpenRuns,
            onOpenUsage = onOpenUsage,
            onOpenHistory = onOpenHistory,
            onOpenRecipes = onOpenRecipes,
        )

        "capabilities" -> CapabilitiesRoute(
            activeTab = tab,
            onSelectTab = selectTab,
            onOpenSettings = onOpenSettings,
            onOpenRuns = onOpenRuns,
            onOpenUsage = onOpenUsage,
            onOpenMemory = onOpenMemory,
            onOpenSkills = onOpenSkills,
            onOpenRunningApps = onOpenRunningApps,
            onOpenBrowser = onOpenBrowser,
        )

        else -> ChatRoute(
            container = container,
            activeTab = tab,
            onSelectTab = selectTab,
            onOpenActMode = onOpenActMode,
            initialGoal = initialGoal,
        )
    }
}

@Composable
private fun EasyShell(
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onOpenActMode: () -> Unit,
    initialGoal: String,
    shared: Boolean,
) {
    var tab by rememberSaveable { mutableStateOf("talk") }
    androidx.compose.runtime.LaunchedEffect(shared) {
        if (shared) {
            tab = "talk"
        }
    }
    val select: (String) -> Unit = { key ->
        when (key) {
            "settings" -> onOpenSettings()
            "talk", "history" -> tab = key
        }
    }

    if (tab == "history") {
        EasyHistoryRoute(
            active = tab,
            onSelectTab = select,
            onOpen = { id ->
                container.selectedConversation.value = id
                tab = "talk"
            },
        )
    } else {
        ChatRoute(
            container = container,
            activeTab = tab,
            onSelectTab = select,
            onOpenActMode = onOpenActMode,
            initialGoal = initialGoal,
            easy = true,
            onOpenSettings = onOpenSettings,
        )
    }
}

@Composable
private fun PowerShell(
    container: AppContainer,
    onOpenSettings: () -> Unit,
    onOpenRuns: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onOpenRecipes: () -> Unit = {},
    onOpenMemory: () -> Unit,
    onOpenSkills: () -> Unit,
    onOpenRunningApps: () -> Unit,
    onOpenActMode: () -> Unit,
    onOpenBrowser: () -> Unit,
    initialGoal: String,
    shared: Boolean,
) {
    var tab by rememberSaveable { mutableStateOf("chat") }
    var chatStarted by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(shared) {
        if (shared) {
            chatStarted = true
            tab = "chat"
        }
    }

    val select: (String) -> Unit = { key ->
        when (key) {
            "chat" -> {
                chatStarted = true
                tab = "chat"
            }

            "activity", "capabilities" -> tab = key
        }
    }

    when {
        tab == "activity" -> ActivityRoute(
            activeTab = tab,
            onSelectTab = select,
            onOpenRuns = onOpenRuns,
            onOpenUsage = onOpenUsage,
            onOpenHistory = onOpenHistory,
            onOpenRecipes = onOpenRecipes,
        )

        tab == "capabilities" -> CapabilitiesRoute(
            activeTab = tab,
            onSelectTab = select,
            onOpenSettings = onOpenSettings,
            onOpenRuns = onOpenRuns,
            onOpenUsage = onOpenUsage,
            onOpenMemory = onOpenMemory,
            onOpenSkills = onOpenSkills,
            onOpenRunningApps = onOpenRunningApps,
            onOpenBrowser = onOpenBrowser,
        )

        chatStarted -> ChatRoute(
            container = container,
            activeTab = tab,
            onSelectTab = select,
            onOpenActMode = onOpenActMode,
            initialGoal = initialGoal,
        )

        else -> PowerHomeRoute(
            active = "chat",
            onSelectTab = select,
            onRun = { goal ->
                container.pendingGoals.trySend(goal)
                chatStarted = true
                tab = "chat"
            },
        )
    }
}

package com.hopa.app.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.hopa.app.di.AppContainer

@Composable
fun SettingsRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenCapabilities: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSelfCheck: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("") }
    val privacy by com.hopa.app.privacy.TurnLog.entries.collectAsStateWithLifecycle()
    var recipes by rememberSaveable { mutableStateOf(emptyList<com.hopa.turn.recipe.Recipe>()) }
    var history by rememberSaveable {
        mutableStateOf(emptyList<com.hopa.data.database.entity.RunHistoryEntity>())
    }
    var historyFilter by rememberSaveable { mutableStateOf("") }
    var appNames by rememberSaveable { mutableStateOf(emptyMap<String, String>()) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val backToHub = { page = "" }

    androidx.compose.runtime.LaunchedEffect(page) {
        if (page == PAGE_RECIPES) {
            recipes = container.recipes.all()
            appNames = runCatching {
                val installed = container.packages.installed()
                com.hopa.catalog.CatalogRoutes.targets(installed, "android", container.knowledge)
                    .filter { it.bundle.isNotBlank() }
                    .associate { it.bundle.lowercase() to it.display }
            }.getOrDefault(emptyMap())
        }
        if (page == PAGE_HISTORY) {
            history = container.runHistory.latest(historyFilter.ifBlank { null })
        }
    }

    // A page slides in from the side it belongs to: deeper to the right, back to
    // the left, with the hub itself as depth zero.
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            val deeper = targetState.isNotEmpty()
            val enter = slideInHorizontally { width -> if (deeper) width / 4 else -width / 4 } +
                fadeIn()
            val leave = slideOutHorizontally { width -> if (deeper) -width / 4 else width / 4 } +
                fadeOut()
            (enter togetherWith leave).using(SizeTransform(clip = false))
        },
        label = "settings-page",
    ) { current ->
        when (current) {
            PAGE_MODEL -> SettingsModelScreen(
                state = state,
                onBack = backToHub,
                onSave = viewModel::save,
                onTestModel = viewModel::testModel,
            )

            PAGE_CHARACTER -> SettingsCharacterScreen(
                state = state,
                onBack = backToHub,
                onTone = viewModel::setTone,
                onChattiness = viewModel::setChattiness,
                onMascot = viewModel::setMascotFramed,
                onOpenProfiles = onOpenProfiles,
            )

            PAGE_HISTORY -> SettingsHistoryScreen(
                rows = history,
                selected = historyFilter,
                onFilter = {
                    historyFilter = it
                    scope.launch {
                        history = container.runHistory.latest(it.ifBlank { null })
                    }
                },
                onBack = backToHub,
                onClear = {
                    scope.launch {
                        container.runHistory.clear()
                        history = emptyList()
                    }
                },
            )

            PAGE_RECIPES -> SettingsRecipesScreen(
                recipes = recipes,
                names = appNames,
                onBack = backToHub,
                onDelete = { goal ->
                    container.recipes.delete(goal)
                    recipes = container.recipes.all()
                },
            )

            PAGE_PRIVACY -> SettingsPrivacyScreen(
                entries = privacy,
                onBack = backToHub,
            )

            PAGE_NOTIFICATIONS -> SettingsNotificationsScreen(
                state = state,
                onBack = backToHub,
                onSave = viewModel::save,
                onTestNotification = viewModel::testNotification,
            )

            else -> SettingsScreen(
                state = state,
                onBack = onBack,
                onOpenPage = { page = it },
                onTone = viewModel::setTone,
                onChattiness = viewModel::setChattiness,
                onMascot = viewModel::setMascotFramed,
                onToggleAlerts = viewModel::setNotifications,
                onOpenCapabilities = onOpenCapabilities,
                onOpenProfiles = onOpenProfiles,
                onOpenSelfCheck = onOpenSelfCheck,
                onOpenAbout = onOpenAbout,
            )
        }
    }
}

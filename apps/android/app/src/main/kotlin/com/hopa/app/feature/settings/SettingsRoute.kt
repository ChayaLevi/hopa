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
import com.hopa.app.di.AppContainer

@Composable
fun SettingsRoute(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenCapabilities: () -> Unit,
    onOpenLaunchpad: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenSelfCheck: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf("") }
    val backToHub = { page = "" }

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

            PAGE_SCREEN -> SettingsScreenAccessScreen(
                state = state,
                onBack = backToHub,
                onScreenPath = viewModel::setScreenPath,
                onStartBackgroundHelper = viewModel::startBackgroundHelper,
                onPairWireless = viewModel::pairWireless,
                onRunCheck = viewModel::runScreenCheck,
            )

            PAGE_BRIDGE -> SettingsBridgeScreen(
                state = state,
                onBack = backToHub,
                onSave = viewModel::save,
                onTestBridge = viewModel::testBridge,
                onPairBridge = viewModel::pairBridge,
            )

            PAGE_CHARACTER -> SettingsCharacterScreen(
                state = state,
                onBack = backToHub,
                onTone = viewModel::setTone,
                onChattiness = viewModel::setChattiness,
                onOpenProfiles = onOpenProfiles,
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
                onOpenCapabilities = onOpenCapabilities,
                onOpenLaunchpad = onOpenLaunchpad,
                onOpenProfiles = onOpenProfiles,
                onOpenAccessibility = onOpenAccessibility,
                onOpenSelfCheck = onOpenSelfCheck,
                onOpenAbout = onOpenAbout,
            )
        }
    }
}

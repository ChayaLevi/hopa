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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.hopa.feature.settings.PAGE_NOTIFICATIONS
import com.hopa.feature.settings.PAGE_PRIVACY
import com.hopa.feature.settings.SettingsNotificationsScreen
import com.hopa.feature.settings.SettingsScreen
import com.hopa.feature.settings.SettingsViewModel

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenSelfCheck: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Always open on the hub: the screen keeps the page only while the person
    // walks inside Settings, not after they leave it.
    var page by remember { mutableStateOf("") }
    val privacy by com.hopa.privacy.TurnLog.entries.collectAsStateWithLifecycle()
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
            PAGE_PRIVACY -> SettingsPrivacyScreen(
                entries = privacy,
                onBack = backToHub,
            )

            PAGE_NOTIFICATIONS -> SettingsNotificationsScreen(
                state = state,
                onBack = backToHub,
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
                onOpenProfiles = onOpenProfiles,
                onOpenSelfCheck = onOpenSelfCheck,
                onOpenAbout = onOpenAbout,
            )
        }
    }
}

package com.hopa.app.feature.settings

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor

/**
 * The settings hub: one row per subject with its current state underneath. The
 * detail lives on its own page, so nothing here needs scrolling past a wall of
 * fields to reach the next idea.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onOpenPage: (String) -> Unit,
    onOpenCapabilities: () -> Unit,
    onOpenLaunchpad: () -> Unit,
    onOpenProfiles: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenSelfCheck: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val settings = state.settings
    SettingsPage(title = "Settings", onBack = onBack) {
        SettingsSection("Assistant")
        SettingsRow(
            title = "Model",
            subtitle = "${providerSummary(state)} - ${settings.model}",
            onClick = { onOpenPage(PAGE_MODEL) },
        )
        SettingsRow(
            title = "Character",
            subtitle = "${settings.tone} - ${settings.chattiness}",
            onClick = { onOpenPage(PAGE_CHARACTER) },
        )
        SettingsRow(
            title = "Notifications",
            subtitle = if (settings.notifyAnswers) "On when an answer is ready" else "Off",
            onClick = { onOpenPage(PAGE_NOTIFICATIONS) },
        )

        SettingsSection("Phone")
        SettingsRow(
            title = "Screen access",
            subtitle = pathLabel(settings.screenPath),
            onClick = { onOpenPage(PAGE_SCREEN) },
        )
        SettingsRow(
            title = "Phone access",
            subtitle = "Read and tap other apps",
            onClick = onOpenAccessibility,
        )

        SettingsSection("Computer")
        SettingsRow(
            title = "PC bridge",
            subtitle = settings.bridgeUrl.ifBlank { "Not paired" },
            onClick = { onOpenPage(PAGE_BRIDGE) },
        )

        SettingsSection("More")
        SettingsRow("Capabilities", "What HOPA can use on this phone", onOpenCapabilities)
        SettingsRow("Launchpad", "Apps installed on this phone", onOpenLaunchpad)
        SettingsRow("Profiles", "Everyday and other modes", onOpenProfiles)
        SettingsRow("Self-check", "Diagnostics and counters", onOpenSelfCheck)
        SettingsRow("About", "Version, licences, the mascot", onOpenAbout)

        state.error?.let { error ->
            Text(error, color = HopaColor.Danger, fontSize = 12.sp)
        }
    }
}

internal const val PAGE_MODEL = "model"
internal const val PAGE_CHARACTER = "character"
internal const val PAGE_NOTIFICATIONS = "notifications"
internal const val PAGE_SCREEN = "screen"
internal const val PAGE_BRIDGE = "bridge"

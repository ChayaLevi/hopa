package com.hopa.app.feature.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor

/**
 * The settings hub: grouped rows with a leading icon, one subject per row,
 * detail on its own page.
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
        SettingsGroup {
            SettingsRow(
                "Model",
                "${providerSummary(state)} - ${settings.model}",
                Icons.Filled.Build,
            ) { onOpenPage(PAGE_MODEL) }
            SettingsDivider()
            SettingsRow(
                "Character",
                "${settings.tone} - ${settings.chattiness}",
                Icons.Filled.Face,
            ) { onOpenPage(PAGE_CHARACTER) }
            SettingsDivider()
            SettingsRow(
                "Notifications",
                if (settings.notifyAnswers) "On when an answer is ready" else "Off",
                Icons.Filled.Notifications,
            ) { onOpenPage(PAGE_NOTIFICATIONS) }
        }

        SettingsSection("Phone")
        SettingsGroup {
            SettingsRow(
                "Screen access",
                pathLabel(settings.screenPath),
                Icons.Filled.Settings,
            ) { onOpenPage(PAGE_SCREEN) }
            SettingsDivider()
            SettingsRow(
                "Phone access",
                "Read and tap other apps",
                Icons.Filled.Lock,
            ) { onOpenAccessibility() }
        }

        SettingsSection("Computer")
        SettingsGroup {
            SettingsRow(
                "PC bridge",
                settings.bridgeUrl.ifBlank { "Not paired" },
                Icons.Filled.Share,
            ) { onOpenPage(PAGE_BRIDGE) }
        }

        SettingsSection("More")
        SettingsGroup {
            SettingsRow(
                "Capabilities",
                "What HOPA can use on this phone",
                Icons.Filled.List,
            ) { onOpenCapabilities() }
            SettingsDivider()
            SettingsRow(
                "Launchpad",
                "Apps installed on this phone",
                Icons.Filled.Menu,
            ) { onOpenLaunchpad() }
            SettingsDivider()
            SettingsRow(
                "Profiles",
                "Everyday and other modes",
                Icons.Filled.Person,
            ) { onOpenProfiles() }
            SettingsDivider()
            SettingsRow(
                "Self-check",
                "Diagnostics and counters",
                Icons.Filled.CheckCircle,
            ) { onOpenSelfCheck() }
            SettingsDivider()
            SettingsRow(
                "About",
                "Version, licences, the mascot",
                Icons.Filled.Info,
            ) { onOpenAbout() }
        }

        state.error?.let { error ->
            Text(error, color = HopaColor.Danger, fontSize = 13.sp)
        }
    }
}

internal const val PAGE_MODEL = "model"
internal const val PAGE_CHARACTER = "character"
internal const val PAGE_NOTIFICATIONS = "notifications"
internal const val PAGE_SCREEN = "screen"
internal const val PAGE_BRIDGE = "bridge"

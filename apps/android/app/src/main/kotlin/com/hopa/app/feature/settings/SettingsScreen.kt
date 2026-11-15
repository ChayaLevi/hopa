package com.hopa.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor

/**
 * Settings as a state dashboard: two live tiles for the things that decide
 * whether HOPA works, then short groups for the rest. No uppercase labels, no
 * paragraph explanations on this page.
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
    val modelOk = state.providerState == com.hopa.capabilities.llm.ProviderState.CLOUD ||
        state.providerState == com.hopa.capabilities.llm.ProviderState.LAN
    val ready = modelOk && state.phoneAccess

    SettingsPage(title = "", onBack = onBack) {
        SettingsHeader(
            title = "Settings",
            summary = when {
                ready -> "Everything HOPA needs is on."
                !modelOk && !state.phoneAccess -> "Two things need you: model and phone access."
                !modelOk -> "Add a model key to get real answers."
                else -> "Turn on phone access so HOPA can act."
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsTile(
                title = "Model",
                value = providerSummary(state),
                state = if (modelOk) DotState.Good else DotState.Attention,
                icon = Icons.Filled.Build,
                onClick = { onOpenPage(PAGE_MODEL) },
                modifier = Modifier.weight(1f),
            )
            SettingsTile(
                title = "Screen access",
                value = shortPathLabel(settings.screenPath),
                state = if (state.phoneAccess) DotState.Good else DotState.Off,
                icon = Icons.Filled.Settings,
                onClick = { onOpenPage(PAGE_SCREEN) },
                modifier = Modifier.weight(1f),
            )
        }

        SettingsSection("Assistant")
        SettingsGroup {
            SettingsRow(
                "Character",
                "${settings.tone} - ${settings.chattiness}",
                Icons.Filled.Face,
            ) { onOpenPage(PAGE_CHARACTER) }
            SettingsDivider()
            SettingsRow(
                "Notifications",
                if (settings.notifyAnswers) "On" else "Off",
                Icons.Filled.Notifications,
            ) { onOpenPage(PAGE_NOTIFICATIONS) }
            SettingsDivider()
            SettingsRow(
                "PC bridge",
                settings.bridgeUrl.ifBlank { "Not paired" },
                Icons.Filled.Share,
            ) { onOpenPage(PAGE_BRIDGE) }
        }

        SettingsSection("Phone")
        SettingsGroup {
            SettingsRow(
                "Phone access",
                if (state.phoneAccess) "On" else "Off",
                Icons.Filled.Lock,
            ) { onOpenAccessibility() }
            SettingsDivider()
            SettingsRow("Capabilities", "What HOPA can use", Icons.Filled.List) {
                onOpenCapabilities()
            }
            SettingsDivider()
            SettingsRow("Launchpad", "Apps on this phone", Icons.Filled.Menu) {
                onOpenLaunchpad()
            }
        }

        SettingsSection("More")
        SettingsGroup {
            SettingsRow("Profiles", "Everyday and other modes", Icons.Filled.Person) {
                onOpenProfiles()
            }
            SettingsDivider()
            SettingsRow("Self-check", "Diagnostics", Icons.Filled.List) { onOpenSelfCheck() }
            SettingsDivider()
            SettingsRow("About", "Version and licences", Icons.Filled.Info) { onOpenAbout() }
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

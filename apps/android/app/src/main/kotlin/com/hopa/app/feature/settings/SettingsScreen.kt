package com.hopa.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor
import com.hopa.capabilities.llm.ProviderState

/**
 * Settings as a dashboard: two tiles for the things that decide whether HOPA can
 * help at all, then short groups. Plain words: no mechanism on this page.
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
    val answers = state.providerState == ProviderState.CLOUD ||
        state.providerState == ProviderState.LAN

    SettingsPage(title = "Settings", onBack = onBack, collapseTitle = true) {
        SettingsHeader(
            title = "Settings",
            summary = when {
                answers && state.phoneAccess -> "Everything is ready."
                !answers && !state.phoneAccess -> "Two things need you below."
                !answers -> "Add a key so HOPA can answer."
                else -> "Allow screen control so HOPA can help."
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsTile(
                title = "Answers",
                value = providerSummary(state),
                state = if (answers) DotState.Good else DotState.Attention,
                icon = Icons.Filled.Star,
                onClick = { onOpenPage(PAGE_MODEL) },
                modifier = Modifier.weight(1f),
            )
            SettingsTile(
                title = "Running apps",
                value = shortPathLabel(settings.screenPath),
                state = if (state.phoneAccess) DotState.Good else DotState.Off,
                icon = Icons.Filled.PlayArrow,
                onClick = { onOpenPage(PAGE_SCREEN) },
                modifier = Modifier.weight(1f),
            )
        }

        SettingsSection("How HOPA talks")
        SettingsGroup {
            SettingsRow(
                "Tone and length",
                "${settings.tone.replaceFirstChar { it.uppercase() }}, ${settings.chattiness}",
                Icons.Filled.Face,
            ) { onOpenPage(PAGE_CHARACTER) }
            SettingsDivider()
            SettingsRow(
                "Alerts",
                if (settings.notifyAnswers) "On" else "Off",
                Icons.Filled.Notifications,
            ) { onOpenPage(PAGE_NOTIFICATIONS) }
            SettingsDivider()
            SettingsRow("Modes", "Everyday and others", Icons.Filled.Menu) {
                onOpenProfiles()
            }
        }

        SettingsSection("This phone")
        SettingsGroup {
            SettingsRow(
                "Screen control",
                if (state.phoneAccess) "On" else "Off",
                Icons.Filled.Lock,
            ) { onOpenAccessibility() }
            SettingsDivider()
            SettingsRow("Apps on this phone", "Everything HOPA can open", Icons.Filled.List) {
                onOpenLaunchpad()
            }
            SettingsDivider()
            SettingsRow("What HOPA can do", "Tools it may use", Icons.Filled.Info) {
                onOpenCapabilities()
            }
        }

        SettingsSection("More")
        SettingsGroup {
            SettingsRow(
                "My computer",
                settings.bridgeUrl.ifBlank { "Not connected" },
                Icons.Filled.Share,
            ) { onOpenPage(PAGE_BRIDGE) }
            SettingsDivider()
            SettingsRow("Health check", "Test everything quickly", Icons.Filled.Info) {
                onOpenSelfCheck()
            }
            SettingsDivider()
            SettingsRow("About", "Version and licences", Icons.Filled.Person) { onOpenAbout() }
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

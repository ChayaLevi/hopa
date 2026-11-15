package com.hopa.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor
import com.hopa.capabilities.llm.ProviderState

/**
 * Settings as one control surface, not a menu: a readiness ring, controls that
 * act where they are, and one-tap fixes for the thing that is missing.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onOpenPage: (String) -> Unit,
    onScreenPath: (String) -> Unit,
    onTone: (String) -> Unit,
    onChattiness: (String) -> Unit,
    onToggleAlerts: (Boolean) -> Unit,
    onRunCheck: () -> Unit,
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
    val ready = (if (answers) 1 else 0) + (if (state.phoneAccess) 1 else 0)

    SettingsPage(title = "Settings", onBack = onBack, collapseTitle = true) {
        SettingsHeader(
            title = "Settings",
            summary = if (ready == 2) {
                "Ready to help."
            } else {
                "One step left before HOPA can act for you."
            },
        )

        // Hero: readiness and the two facts that decide it.
        SettingsPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusRing(ready = ready, total = 2)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusLine(
                        "Answers",
                        if (answers) providerSummary(state) else "Needs a key",
                        answers,
                    ) { onOpenPage(PAGE_MODEL) }
                    StatusLine(
                        "Screen control",
                        if (state.phoneAccess) "On" else "Off",
                        state.phoneAccess,
                    ) { onOpenAccessibility() }
                }
            }
        }

        // The fix, offered as one tap instead of a page to find.
        if (ready < 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickChip("Add a key") { onOpenPage(PAGE_MODEL) }
                QuickChip("Allow screen control") { onOpenAccessibility() }
            }
        }

        SettingsPanel {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenPage(PAGE_SCREEN) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Running apps", fontSize = 15.sp, color = HopaColor.TextPrimary)
                    Text(
                        "Where apps open when you ask.",
                        fontSize = 13.sp,
                        color = HopaColor.TextSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text("\u203a", fontSize = 20.sp, color = HopaColor.TextHint)
            }
            SettingsSegment(
                options = listOf(
                    "auto" to "Auto",
                    "background" to "Hidden",
                    "onscreen" to "Here",
                    "bridge" to "PC",
                ),
                selected = settings.screenPath,
                onSelect = onScreenPath,
                modifier = Modifier.padding(top = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onRunCheck, enabled = !state.busy) {
                    Text(if (state.busy) "Checking\u2026" else "Check it works")
                }
                TextButton(onClick = { onOpenPage(PAGE_SCREEN) }) {
                    Text("How each mode works")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsPanel(modifier = Modifier.weight(1f)) {
                Text("Tone", fontSize = 13.sp, color = HopaColor.TextSecondary)
                SettingsSegment(
                    options = listOf("friendly" to "Friendly", "direct" to "Direct"),
                    selected = settings.tone,
                    onSelect = onTone,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Length",
                    fontSize = 13.sp,
                    color = HopaColor.TextSecondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
                SettingsSegment(
                    options = listOf(
                        "short" to "Short",
                        "balanced" to "Balanced",
                        "detailed" to "Long",
                    ),
                    selected = settings.chattiness,
                    onSelect = onChattiness,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        SettingsPanel {
            SettingsSwitchRow("Alerts when an answer is ready", settings.notifyAnswers) {
                onToggleAlerts(it)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickChip("Apps") { onOpenLaunchpad() }
            QuickChip("Modes") { onOpenProfiles() }
            QuickChip("Tools") { onOpenCapabilities() }
            QuickChip("Health") { onOpenSelfCheck() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuickChip("My computer") { onOpenPage(PAGE_BRIDGE) }
            QuickChip("About") { onOpenAbout() }
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

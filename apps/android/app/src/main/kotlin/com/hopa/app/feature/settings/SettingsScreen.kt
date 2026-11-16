package com.hopa.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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

        // Two facts decide whether HOPA can help; nothing else belongs up here.
        SettingsPanel {
            Column {
                StatusLine(
                    "Answers",
                    if (answers) providerSummary(state) else "needs a key",
                    answers,
                ) { onOpenPage(PAGE_MODEL) }
                StatusLine(
                    "Screen control",
                    if (state.phoneAccess) "on" else "off",
                    state.phoneAccess,
                ) { onOpenAccessibility() }
                SettingsDivider()
                if (ready < 2) {
                    Row(
                        modifier = Modifier.padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!answers) {
                            QuickChip("Add a key") { onOpenPage(PAGE_MODEL) }
                        }
                        if (!state.phoneAccess) {
                            QuickChip("Allow screen control") { onOpenAccessibility() }
                        }
                    }
                }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onRunCheck,
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (state.busy) "Checking\u2026" else "Check that it works",
                        fontSize = 13.sp,
                    )
                }
                OutlinedButton(
                    onClick = { onOpenPage(PAGE_SCREEN) },
                    modifier = Modifier,
                ) {
                    Text("Set up", fontSize = 13.sp)
                }
            }
            // A button that reports nowhere is a broken button.
            state.screenCheck.take(2).forEach { check ->
                CheckRow(check) { onOpenPage(PAGE_SCREEN) }
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
            QuickChip("Privacy") { onOpenPage(PAGE_PRIVACY) }
            QuickChip("Repeats") { onOpenPage(PAGE_RECIPES) }
            QuickChip("History") { onOpenPage(PAGE_HISTORY) }
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
internal const val PAGE_PRIVACY = "privacy"
internal const val PAGE_RECIPES = "recipes"
internal const val PAGE_HISTORY = "history"

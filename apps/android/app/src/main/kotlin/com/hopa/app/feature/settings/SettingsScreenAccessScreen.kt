package com.hopa.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor

/**
 * Choose where apps open. A scene shows what will happen, four tiles choose it,
 * one button proves it, and setup happens in a dialog you can actually see.
 */
@Composable
fun SettingsScreenAccessScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onScreenPath: (String) -> Unit,
    onStartBackgroundHelper: () -> Unit,
    onPairWireless: (String, String) -> Unit,
    onRunCheck: () -> Unit,
    onOpenBridge: () -> Unit,
    onOpenScreenControl: () -> Unit,
    onPlanFirst: (Boolean) -> Unit,
    onReplaySkills: (Boolean) -> Unit,
) {
    var setup by rememberSaveable { mutableStateOf(false) }
    val mode = state.settings.screenPath
    val bridgeReady = state.settings.bridgeUrl.isNotBlank()

    SettingsPage(title = "Running apps", onBack = onBack) {
        ModeSceneSwitch(mode = mode)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeTile("auto", "Auto", mode == "auto", { onScreenPath("auto") }, Modifier.weight(1f))
            ModeTile(
                "background",
                "Hidden",
                mode == "background",
                { onScreenPath("background") },
                Modifier.weight(1f),
            )
            ModeTile(
                "onscreen",
                "Here",
                mode == "onscreen",
                { onScreenPath("onscreen") },
                Modifier.weight(1f),
            )
            ModeTile(
                "bridge",
                "Computer",
                mode == "bridge",
                { onScreenPath("bridge") },
                Modifier.weight(1f),
            )
        }

        Text(
            when (mode) {
                "background" -> "Apps open out of sight. Turn it on once, then just ask."
                "onscreen" -> "HOPA uses your screen for a moment; you see every tap."
                "bridge" -> "Your computer does the tapping. Works while it is on."
                else -> "Picks the best available: hidden, computer, then your screen."
            },
            style = MaterialTheme.typography.bodySmall,
        )

        Button(
            onClick = onRunCheck,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.busy) "Checking\u2026" else "Check that it works")
        }
        if (state.screenCheck.isEmpty()) {
            Text(
                "Opens Settings for you, reads it and closes it again.",
                style = MaterialTheme.typography.bodySmall,
                color = HopaColor.TextHint,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                state.screenCheck.take(4).forEach { check ->
                    CheckRow(check) { picked ->
                        when (picked.id) {
                            "screen" -> onOpenScreenControl()
                            "background" -> setup = true
                            "computer" -> onOpenBridge()
                        }
                    }
                }
            }
        }

        state.bridgeStatus?.let { message ->
            Text(message, fontSize = 13.sp, color = HopaColor.TextSecondary)
        }

        if (mode != "onscreen") {
            SettingsGroup {
                SettingsRow(
                    if (bridgeReady) "Turn on background mode" else "Set up background mode",
                    if (bridgeReady) "One press, then just ask" else "Two steps, about a minute",
                    Icons.Filled.Menu,
                ) {
                    if (bridgeReady) onStartBackgroundHelper() else setup = true
                }
            }
        }

        SettingsPanel {
            SettingsSwitchRow("Show me the plan first", state.settings.planFirst) {
                onPlanFirst(it)
            }
            Text(
                "Nothing gets tapped until you approve the numbered plan.",
                fontSize = 12.sp,
                color = HopaColor.TextHint,
            )
            SettingsDivider()
            SettingsSwitchRow("Repeat what worked before", state.settings.replaySkills) {
                onReplaySkills(it)
            }
            Text(
                "A goal that succeeded runs the same steps again without asking the " +
                    "model. If a screen changed, HOPA stops and thinks instead.",
                fontSize = 12.sp,
                color = HopaColor.TextHint,
            )
        }

        Text(
            "Background mode survives restarts; after a phone reboot, press it once more.",
            fontSize = 12.sp,
            color = HopaColor.TextHint,
        )
    }

    if (setup) {
        BackgroundSetupDialog(
            busy = state.busy,
            onDismiss = { setup = false },
            onConnect = { address, code ->
                onPairWireless(address, code)
                setup = false
            },
        )
    }
}

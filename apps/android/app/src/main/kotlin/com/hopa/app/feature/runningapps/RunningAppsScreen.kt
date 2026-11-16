package com.hopa.app.feature.runningapps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
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
import com.hopa.app.feature.settings.BackgroundSetupDialog
import com.hopa.app.feature.settings.CheckRow
import com.hopa.app.feature.settings.ModeSceneSwitch
import com.hopa.app.feature.settings.ModeTile
import com.hopa.app.feature.settings.SettingsDivider
import com.hopa.app.feature.settings.SettingsGroup
import com.hopa.app.feature.settings.SettingsPage
import com.hopa.app.feature.settings.SettingsPanel
import com.hopa.app.feature.settings.SettingsRow
import com.hopa.app.feature.settings.SettingsSection
import com.hopa.app.feature.settings.SettingsSwitchRow
import com.hopa.app.feature.settings.SettingsUiState
import com.hopa.app.ui.theme.HopaColor

/**
 * One page for everything about opening another app: which surface runs the
 * taps, what that surface needs before it works, the apps themselves, and the
 * three switches that change a run. Settings owns none of this any more.
 */
@Composable
fun RunningAppsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onScreenPath: (String) -> Unit,
    onStartBackgroundHelper: () -> Unit,
    onPairWireless: (String, String) -> Unit,
    onRunCheck: () -> Unit,
    onPlanFirst: (Boolean) -> Unit,
    onVision: (Boolean) -> Unit,
    onReplaySkills: (Boolean) -> Unit,
    onOpenLaunchpad: () -> Unit,
    onOpenScreenAccess: () -> Unit,
    onOpenComputer: () -> Unit,
) {
    var setup by rememberSaveable { mutableStateOf(false) }
    val mode = state.settings.screenPath
    val bridgeReady = state.settings.bridgeUrl.isNotBlank()

    SettingsPage(title = "Running apps", onBack = onBack) {
        Text(
            "Where apps open when you ask, what it takes to open them, and the " +
                "apps themselves.",
            style = MaterialTheme.typography.bodySmall,
            color = HopaColor.TextHint,
        )
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
        SurfaceLine("Hidden", "A screen you cannot see, with the same apps. Needs the background helper.")
        SurfaceLine("Here", "Your screen, and you watch every tap. Needs screen access.")
        SurfaceLine("Computer", "A paired PC does the taps over adb. Needs the bridge switched on.")
        SurfaceLine("Auto", "Tries hidden, then computer, then here, and says which one it used.")
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
                            "screen" -> onOpenScreenAccess()
                            "background" -> setup = true
                            "computer" -> onOpenComputer()
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

        SettingsSection("Apps")
        SettingsGroup {
            SettingsRow(
                "Apps and sites",
                "The catalog of everything HOPA can open here",
                Icons.Filled.List,
            ) {
                onOpenLaunchpad()
            }
            SettingsDivider()
            SettingsRow(
                "Screen access",
                if (state.phoneAccess) "On - HOPA can see and tap" else "Off - needed to run anything",
                Icons.Filled.Lock,
            ) {
                onOpenScreenAccess()
            }
            SettingsDivider()
            SettingsRow(
                "My computer",
                if (bridgeReady) "Paired" else "Not set up",
                Icons.Filled.Share,
            ) {
                onOpenComputer()
            }
        }

        SettingsSection("When it runs")
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
            SettingsSwitchRow("Look at the screen too", state.settings.vision) {
                onVision(it)
            }
            Text(
                "Sends a screenshot with the request. Only works with a model that " +
                    "accepts pictures, and it costs more.",
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

/** One sentence about what a surface can actually do. */
@Composable
private fun SurfaceLine(name: String, detail: String) {
    Row(
        modifier = Modifier.padding(start = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("\u00b7", fontSize = 12.sp, color = HopaColor.TextHint)
        Text(
            name,
            fontSize = 12.sp,
            color = HopaColor.TextSecondary,
        )
        Text(detail, fontSize = 12.sp, color = HopaColor.TextHint)
    }
}

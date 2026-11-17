package com.hopa.app.feature.runningapps

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.feature.settings.BackgroundSetupDialog
import com.hopa.app.feature.settings.SettingsUiState
import com.hopa.app.ui.component.HopaPageHeader
import com.hopa.app.ui.component.HopaStatusPill
import com.hopa.app.ui.theme.HopaColor
import com.hopa.core.router.RouteTarget

/**
 * The one home for everything HOPA can act on: where it acts, which apps and
 * sites it can open, and the setup and preferences behind that. The lists and
 * the switches live on their own pages; this page answers "where does HOPA
 * work right now, and what can it reach".
 */
@Composable
fun RunningAppsScreen(
    state: SettingsUiState,
    apps: List<RouteTarget>,
    pairingStatus: String?,
    onBack: () -> Unit,
    onScreenPath: (String) -> Unit,
    onStartBackgroundHelper: () -> Unit,
    onPairWireless: (String, String) -> Unit,
    onPairWirelessFromScreen: () -> Unit,
    onPairWirelessWaiting: () -> Unit,
    onRunCheck: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenRunPrefs: () -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenSites: () -> Unit,
    onOpenScreenAccess: () -> Unit,
    onOpenComputer: () -> Unit,
) {
    var setup by rememberSaveable { mutableStateOf(false) }
    val mode = state.settings.screenPath
    val bridgeReady = state.settings.bridgeUrl.isNotBlank()
    val hiddenForced = mode == "background" && !state.backgroundRunning
    val needsScreen = !state.phoneAccess
    val needsHidden = mode == "background" && !state.backgroundRunning

    Scaffold(
        containerColor = HopaColor.Bg,
        topBar = {
            HopaPageHeader(
                title = "Act",
                onBack = onBack,
                status = {
                    HopaStatusPill(
                        text = when {
                            needsScreen -> "Needs screen access"
                            hiddenForced -> "Hidden screen is off"
                            else -> "Ready"
                        },
                        color = if (needsScreen || hiddenForced) HopaColor.Warning else HopaColor.Success,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Where HOPA acts", fontSize = 12.sp, color = HopaColor.TextHint)
            SurfaceChoice(mode = mode, onPick = onScreenPath)
            Text(
                when (mode) {
                    "background" -> "HOPA uses the hidden screen."
                    "bridge" -> "HOPA uses your computer."
                    "onscreen" -> "HOPA uses this phone's screen."
                    else -> "HOPA picks hidden, then computer, then this screen."
                },
                fontSize = 13.sp,
                color = HopaColor.TextSecondary,
            )

            when {
                needsScreen -> FixButton("Turn on screen access", state.busy, onOpenScreenAccess)
                needsHidden && (state.backgroundRunning || bridgeReady) ->
                    FixButton("Start the hidden screen", state.busy, onStartBackgroundHelper)

                needsHidden -> FixButton("Set up the hidden screen", state.busy) { setup = true }
                else -> Unit
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HubRow(
                    title = "Apps",
                    subtitle = "${apps.size} available",
                    onClick = onOpenAllApps,
                )
                HubRow(
                    title = "Sites",
                    subtitle = "Browse the catalog",
                    onClick = onOpenSites,
                )
                HubRow(
                    title = "Setup & test",
                    subtitle = when {
                        needsScreen -> "Screen access is off"
                        state.backgroundRunning -> "Screen access on, hidden screen running"
                        else -> "Screen access on"
                    },
                    onClick = onOpenSetup,
                )
                HubRow(
                    title = "Run preferences",
                    subtitle = runPreferencesLine(state),
                    onClick = onOpenRunPrefs,
                )
            }

            pairingStatus?.let { message ->
                Text(
                    message,
                    fontSize = 12.sp,
                    color = if (message.startsWith("Wireless shell") || message.startsWith("Paired")) {
                        HopaColor.Success
                    } else {
                        HopaColor.Warning
                    },
                )
            }
        }
    }

    if (setup) {
        BackgroundSetupDialog(
            busy = state.busy,
            onDismiss = { setup = false },
            onConnect = { address, code ->
                onPairWireless(address, code)
                setup = false
            },
            onPairAutomatically = {
                setup = false
                onPairWirelessFromScreen()
            },
            onWaitForDialog = {
                setup = false
                onPairWirelessWaiting()
            },
        )
    }
}

@Composable
private fun FixButton(label: String, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
    ) {
        Text(if (busy) "Working\u2026" else label)
    }
}

@Composable
private fun HubRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        color = HopaColor.Surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HopaColor.Divider),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 16.sp, color = HopaColor.TextPrimary, fontWeight = FontWeight.Medium)
                Text(subtitle, fontSize = 12.sp, color = HopaColor.TextHint)
            }
            Text("\u203A", fontSize = 18.sp, color = HopaColor.TextHint)
        }
    }
}

@Composable
private fun SurfaceChoice(mode: String, onPick: (String) -> Unit) {
    val options = listOf(
        "auto" to "Auto",
        "background" to "Hidden",
        "onscreen" to "Phone",
        "bridge" to "Computer",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HopaColor.SurfaceMuted, RoundedCornerShape(100.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (key, label) ->
            val selected = mode == key
            Surface(
                color = if (selected) HopaColor.Surface else androidx.compose.ui.graphics.Color.Transparent,
                shape = RoundedCornerShape(100.dp),
                border = if (selected) BorderStroke(1.dp, HopaColor.Primary) else null,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPick(key) },
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(vertical = 8.dp),
                    fontSize = 13.sp,
                    color = if (selected) HopaColor.Primary else HopaColor.TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

private fun runPreferencesLine(state: SettingsUiState): String {
    val plan = if (state.settings.planFirst) "plan first" else "no plan"
    val vision = if (state.settings.vision) "screenshots" else "no screenshots"
    val replay = if (state.settings.replaySkills) "replay on" else "replay off"
    return "$plan, $vision, $replay"
}

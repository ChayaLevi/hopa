package com.hopa.app.feature.runningapps

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.hopa.app.ui.theme.HopaColor

/**
 * The one home for everything HOPA can act on. Two questions, in order:
 * which machine (this phone or the computer), and — on the phone — whether
 * HOPA works out of sight or on the screen the person is watching. The computer
 * is never picked behind the person's back.
 */
@Composable
fun RunningAppsScreen(
    state: SettingsUiState,
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
    val computer = mode == "bridge"
    val hidden = !computer && mode != "onscreen"

    Scaffold(
        containerColor = HopaColor.Bg,
        topBar = { HopaPageHeader(title = "Act", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Where HOPA works", fontSize = 12.sp, color = HopaColor.TextHint)
            Segmented(
                options = listOf(true to "This phone", false to "My computer"),
                selected = !computer,
                onPick = { phone ->
                    if (phone && computer) {
                        onScreenPath("background")
                    } else if (!phone) {
                        onScreenPath("bridge")
                    }
                },
            )

            if (!computer) {
                Text("On this phone", fontSize = 12.sp, color = HopaColor.TextHint)
                Segmented(
                    options = listOf(true to "Hidden screen", false to "Your screen"),
                    selected = hidden,
                    onPick = { wantsHidden ->
                        onScreenPath(if (wantsHidden) "background" else "onscreen")
                    },
                )
            }

            Column(modifier = Modifier.height(64.dp)) {
                Text(
                    when {
                        computer && !bridgeReady -> "Your computer is not set up."
                        computer -> "HOPA works on your computer."
                        hidden && !state.backgroundRunning -> "The hidden screen is not running."
                        hidden -> "HOPA works out of sight."
                        !state.phoneAccess -> "Screen access is off."
                        else -> "HOPA works on the screen you see."
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = HopaColor.TextPrimary,
                    maxLines = 1,
                )
                Text(
                    when {
                        computer -> "You can watch it on the PC."
                        hidden -> "You keep using your phone; the work happens on a screen you cannot see."
                        else -> "You watch every tap on this screen."
                    },
                    fontSize = 12.sp,
                    color = HopaColor.TextHint,
                    minLines = 2,
                    maxLines = 2,
                )
            }

            when {
                computer && !bridgeReady ->
                    TextButton(onClick = onOpenComputer) { Text("Pair your computer") }

                !computer && hidden && !state.backgroundRunning && bridgeReady ->
                    TextButton(onClick = onStartBackgroundHelper) { Text("Start the hidden screen") }

                !computer && hidden && !state.backgroundRunning ->
                    TextButton(onClick = { setup = true }) { Text("Set up the hidden screen") }

                !computer && !hidden && !state.phoneAccess ->
                    TextButton(onClick = onOpenScreenAccess) { Text("Allow screen access") }

                else -> Unit
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HubRow(
                    title = "Apps",
                    subtitle = "Installed apps HOPA can open",
                    onClick = onOpenAllApps,
                )
                HubRow(
                    title = "Sites",
                    subtitle = "Websites HOPA can open",
                    onClick = onOpenSites,
                )
                HubRow(
                    title = "Setup & test",
                    subtitle = when {
                        !state.phoneAccess -> "Screen access is paused"
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

/** Two-way choice that reads as a sentence, not as a control panel. */
@Composable
private fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onPick: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Surface(
                color = if (active) HopaColor.PrimarySoft else HopaColor.Surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, if (active) HopaColor.Primary else HopaColor.Divider),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPick(value) },
            ) {
                Text(
                    label,
                    modifier = Modifier.padding(vertical = 12.dp),
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) HopaColor.Primary else HopaColor.TextSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
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

private fun runPreferencesLine(state: SettingsUiState): String {
    val plan = if (state.settings.planFirst) "plan first" else "no plan"
    val vision = if (state.settings.vision) "screenshots" else "no screenshots"
    val replay = if (state.settings.replaySkills) "replay on" else "replay off"
    return "$plan, $vision, $replay"
}

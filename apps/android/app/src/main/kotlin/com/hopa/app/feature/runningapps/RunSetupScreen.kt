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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.feature.settings.BackgroundSetupDialog
import com.hopa.app.feature.settings.SettingsUiState
import com.hopa.app.ui.component.HopaPageHeader
import com.hopa.app.ui.theme.HopaColor

/** What a run needs, and the one check that proves it. */
@Composable
fun RunSetupScreen(
    state: SettingsUiState,
    pairingStatus: String?,
    onBack: () -> Unit,
    onOpenScreenAccess: () -> Unit,
    onStartBackgroundHelper: () -> Unit,
    onOpenComputer: () -> Unit,
    onRunCheck: () -> Unit,
    onPairWireless: (String, String) -> Unit,
    onPairWirelessFromScreen: () -> Unit,
    onPairWirelessWaiting: () -> Unit,
) {
    var setup by rememberSaveable { mutableStateOf(false) }
    val bridgeReady = state.settings.bridgeUrl.isNotBlank()

    Scaffold(
        containerColor = HopaColor.Bg,
        topBar = { HopaPageHeader(title = "Setup & test", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color = HopaColor.Surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, HopaColor.Divider),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    SetupRow(
                        "Screen access",
                        when {
                            !state.settings.accessibilityAllowed -> "Paused in HOPA"
                            state.phoneAccess -> "On"
                            else -> "Off"
                        },
                        state.phoneAccess,
                        onOpenScreenAccess,
                    )
                    HairLine()
                    SetupRow(
                        "Hidden screen",
                        when {
                            state.backgroundRunning -> "Running"
                            bridgeReady -> "Start"
                            else -> "Set up"
                        },
                        state.backgroundRunning,
                        {
                            // Running without a bridge cannot be restarted by the
                            // app, so the row opens pairing instead of doing
                            // nothing; an unpaired phone gets the same dialog.
                            if (!state.backgroundRunning && bridgeReady) {
                                onStartBackgroundHelper()
                            } else {
                                setup = true
                            }
                        },
                    )
                    HairLine()
                    SetupRow(
                        "My computer",
                        if (bridgeReady) "Paired" else "Not set up",
                        bridgeReady,
                        onOpenComputer,
                    )
                }
            }

            if (!bridgeReady) {
                Text(
                    "Set up the hidden screen once with a code from Android's wireless debugging.",
                    fontSize = 12.sp,
                    color = HopaColor.TextHint,
                )
            }

            Button(
                onClick = onRunCheck,
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
            ) {
                Text(if (state.busy) "Checking\u2026" else "Check that it works")
            }
            state.screenCheck.take(4).forEach { check ->
                CheckLine(check.text, check.ok) {
                    when (check.id) {
                        "screen" -> onOpenScreenAccess()
                        "background" -> setup = true
                        "computer" -> onOpenComputer()
                    }
                }
            }
            state.bridgeStatus?.let { message ->
                Text(
                    message,
                    fontSize = 12.sp,
                    color = if (message.contains("ready", ignoreCase = true) ||
                        message.startsWith("Paired")
                    ) {
                        HopaColor.Success
                    } else {
                        HopaColor.Warning
                    },
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
private fun SetupRow(title: String, value: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (ok) "\u25CF" else "\u25CB",
            fontSize = 10.sp,
            color = if (ok) HopaColor.Success else HopaColor.Warning,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(title, fontSize = 15.sp, color = HopaColor.TextPrimary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, color = HopaColor.TextHint)
        Text("\u203A", fontSize = 16.sp, color = HopaColor.TextHint, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun CheckLine(text: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (ok) "\u2713" else "\u2717",
            fontSize = 12.sp,
            color = if (ok) HopaColor.Success else HopaColor.Warning,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(text, fontSize = 12.sp, color = HopaColor.TextSecondary)
    }
}

@Composable
private fun HairLine() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(HopaColor.Divider),
    )
}

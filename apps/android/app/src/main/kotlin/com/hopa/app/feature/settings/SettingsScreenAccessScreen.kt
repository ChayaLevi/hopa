package com.hopa.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * How HOPA opens other apps. Each mode says what it does and, once chosen, how to
 * use it - in plain words, with no mechanism named.
 */
@Composable
fun SettingsScreenAccessScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onScreenPath: (String) -> Unit,
    onStartBackgroundHelper: () -> Unit,
    onPairWireless: (String, String) -> Unit,
    onRunCheck: () -> Unit,
) {
    var setup by rememberSaveable { mutableStateOf(false) }
    var wirelessAddress by rememberSaveable { mutableStateOf("") }
    var wirelessCode by rememberSaveable { mutableStateOf("") }

    SettingsPage(title = "Running apps", onBack = onBack) {
        SettingsChoiceCard(
            title = "Automatic",
            description = "HOPA picks the best one available right now.",
            howTo = "Do nothing else. Just ask for something; if a mode does not work, " +
                "HOPA moves to the next one and tells you where it ran.",
            selected = state.settings.screenPath == "auto",
        ) { onScreenPath("auto") }

        SettingsChoiceCard(
            title = "Without showing",
            description = "Apps run out of sight. You keep using your phone.",
            howTo = "Turn it on once: use your computer, or the steps at the bottom. " +
                "After that just ask; the app opens out of sight and your screen stays " +
                "where it is.",
            selected = state.settings.screenPath == "background",
        ) { onScreenPath("background") }

        SettingsChoiceCard(
            title = "On this phone",
            description = "You watch every tap on your own screen.",
            howTo = "Nothing to set up. Ask, and HOPA will take over your screen for a " +
                "moment while it taps. Keep the phone in your hand.",
            selected = state.settings.screenPath == "onscreen",
        ) { onScreenPath("onscreen") }

        SettingsChoiceCard(
            title = "From my computer",
            description = "Your computer does the tapping; the phone screen stays free.",
            howTo = "Start HOPA on your computer, connect it once from Settings, then " +
                "ask from the phone. Works only while the computer is on and connected.",
            selected = state.settings.screenPath == "bridge",
        ) { onScreenPath("bridge") }

        Button(
            onClick = onRunCheck,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.busy) "Checking\u2026" else "Check that it works")
        }
        if (state.screenCheck.isEmpty()) {
            Text(
                "Opens Settings for you, reads it and closes it again. Nothing stays open.",
                style = MaterialTheme.typography.bodySmall,
                color = HopaColor.TextHint,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.screenCheck.forEach { line -> CheckLine(line) }
            }
        }

        state.bridgeStatus?.let { message ->
            Text(message, fontSize = 13.sp, color = HopaColor.TextSecondary)
        }

        if (state.settings.screenPath != "onscreen") {
            SettingsGroup {
                SettingsRow(
                    "Turn on background mode",
                    if (state.settings.bridgeUrl.isBlank()) {
                        "Needs your computer, or the steps below"
                    } else {
                        "Using your computer"
                    },
                    Icons.Filled.Menu,
                ) { if (state.settings.bridgeUrl.isNotBlank()) onStartBackgroundHelper() }
            }
        }

        TextButton(onClick = { setup = !setup }) {
            Text(if (setup) "Hide the steps" else "Set it up without a computer")
        }
        if (setup) {
            Text(
                "1. In Android settings open Developer options, then Wireless debugging, " +
                    "then \"Pair device with pairing code\".\n" +
                    "2. Type the address and the six digits here.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                value = wirelessAddress,
                onValueChange = { wirelessAddress = it },
                label = { Text("Address, e.g. 192.168.1.5:37123") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = wirelessCode,
                onValueChange = { wirelessCode = it },
                label = { Text("Six digits") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onPairWireless(wirelessAddress, wirelessCode) },
                enabled = !state.busy && wirelessAddress.isNotBlank() &&
                    wirelessCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Connect")
            }
        }
    }
}

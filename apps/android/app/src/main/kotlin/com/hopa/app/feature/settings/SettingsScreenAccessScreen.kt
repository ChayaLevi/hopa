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
 * Where runs happen: three cards you can read at a glance, one primary action to
 * prove the chain works, and the pairing form folded behind a link.
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
    var pairing by rememberSaveable { mutableStateOf(false) }
    var wirelessAddress by rememberSaveable { mutableStateOf("") }
    var wirelessCode by rememberSaveable { mutableStateOf("") }

    SettingsPage(title = "Screen access", onBack = onBack) {
        SettingsChoiceCard(
            title = "Auto",
            description = "Hidden first, then the PC, then this screen.",
            selected = state.settings.screenPath == "auto",
        ) { onScreenPath("auto") }
        SettingsChoiceCard(
            title = "Hidden screen",
            description = "Apps run where you cannot see them. Your phone stays put.",
            selected = state.settings.screenPath == "background",
        ) { onScreenPath("background") }
        SettingsChoiceCard(
            title = "This screen",
            description = "HOPA works on your screen and you watch every tap.",
            selected = state.settings.screenPath == "onscreen",
        ) { onScreenPath("onscreen") }
        SettingsChoiceCard(
            title = "PC bridge",
            description = "The computer drives this phone over adb.",
            selected = state.settings.screenPath == "bridge",
        ) { onScreenPath("bridge") }

        Button(
            onClick = onRunCheck,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.busy) "Checking\u2026" else "Check this setup")
        }
        if (state.screenCheck.isEmpty()) {
            Text(
                "Opens Settings on the chosen surface, reads it and closes it again.",
                style = MaterialTheme.typography.bodySmall,
                color = HopaColor.TextHint,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.screenCheck.forEach { line ->
                    CheckLine(line)
                }
            }
        }

        state.bridgeStatus?.let { message ->
            Text(message, fontSize = 13.sp, color = HopaColor.TextSecondary)
        }

        if (state.settings.screenPath == "background" || state.settings.screenPath == "auto") {
            SettingsGroup {
                SettingsRow(
                    "Start the helper",
                    if (state.settings.bridgeUrl.isBlank()) {
                        "Needs the PC bridge, or pair below"
                    } else {
                        "Through the paired PC"
                    },
                    Icons.Filled.Menu,
                ) { if (state.settings.bridgeUrl.isNotBlank()) onStartBackgroundHelper() }
            }
        }

        TextButton(onClick = { pairing = !pairing }) {
            Text(if (pairing) "Hide pairing" else "Pair this phone without a PC")
        }
        if (pairing) {
            Text(
                "Wireless debugging, Pair device with pairing code.",
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
                label = { Text("Six-digit pairing code") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onPairWireless(wirelessAddress, wirelessCode) },
                enabled = !state.busy && wirelessAddress.isNotBlank() &&
                    wirelessCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Pair")
            }
        }
    }
}

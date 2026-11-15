package com.hopa.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
 * Choose where apps open. A scene shows what will happen, four tiles choose it,
 * one button proves it, and the setup steps stay folded until they are needed.
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
) {
    var setup by rememberSaveable { mutableStateOf(false) }
    var wirelessAddress by rememberSaveable { mutableStateOf("") }
    var wirelessCode by rememberSaveable { mutableStateOf("") }
    val mode = state.settings.screenPath

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
            val bridgeReady = state.settings.bridgeUrl.isNotBlank()
            SettingsGroup {
                SettingsRow(
                    if (bridgeReady) "Turn on background mode" else "Show me the steps",
                    if (bridgeReady) "One press, then just ask" else "Takes a minute",
                    Icons.Filled.Menu,
                ) {
                    if (bridgeReady) onStartBackgroundHelper() else setup = true
                }
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

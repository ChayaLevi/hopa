package com.hopa.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor

/** The PC bridge: address, pair code, token, and the two checks. */
@Composable
fun SettingsBridgeScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSave: (String, String, String, String, Boolean, Boolean, String, String, Boolean) -> Unit,
    onTestBridge: (String, String) -> Unit,
    onPairBridge: (String, String) -> Unit,
) {
    var bridgeUrl by rememberSaveable { mutableStateOf(state.settings.bridgeUrl) }
    var pairCode by rememberSaveable { mutableStateOf("") }
    var bridgeKey by rememberSaveable { mutableStateOf("") }
    var useBridge by rememberSaveable { mutableStateOf(state.settings.useBridge) }

    LaunchedEffect(state.settings) {
        bridgeUrl = state.settings.bridgeUrl
        useBridge = state.settings.useBridge
    }

    SettingsPage(title = "PC bridge", onBack = onBack) {
        SettingsSwitchRow("Use the bridge for device actions", useBridge) { useBridge = it }
        OutlinedTextField(
            value = bridgeUrl,
            onValueChange = { bridgeUrl = it },
            label = { Text("Address, e.g. http://192.168.1.5:8766") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = pairCode,
            onValueChange = { pairCode = it },
            label = { Text("Pair code from the bridge console") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = bridgeKey,
            onValueChange = { bridgeKey = it },
            label = { Text("Bridge token, if you have one") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onPairBridge(bridgeUrl, pairCode) }, enabled = !state.busy) {
                Text("Pair")
            }
            Button(onClick = { onTestBridge(bridgeUrl, bridgeKey) }, enabled = !state.busy) {
                Text("Test")
            }
        }
        state.bridgeStatus?.let { message ->
            Text(message, fontSize = 12.sp, color = HopaColor.TextSecondary)
        }
        Button(
            onClick = {
                onSave(
                    state.settings.provider,
                    state.settings.endpoint,
                    state.settings.model,
                    "",
                    state.settings.notifyAnswers,
                    state.settings.webEnabled,
                    bridgeUrl,
                    bridgeKey,
                    useBridge,
                )
            },
            enabled = !state.busy,
        ) {
            Text(if (state.busy) "Saving\u2026" else "Save")
        }
        Text(
            "The bridge runs on your PC and acts on the phone over adb. " +
                "Without it, the phone works on its own.",
            style = MaterialTheme.typography.bodySmall,
            color = HopaColor.TextHint,
        )
    }
}

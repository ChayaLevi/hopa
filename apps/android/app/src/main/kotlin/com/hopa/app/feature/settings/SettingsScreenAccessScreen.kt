package com.hopa.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
 * Where a run happens, what each choice means, and one button that checks the
 * whole chain instead of leaving the person to guess.
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
    var wirelessAddress by rememberSaveable { mutableStateOf("") }
    var wirelessCode by rememberSaveable { mutableStateOf("") }

    SettingsPage(title = "Screen access", onBack = onBack) {
        SettingsSegment(
            options = listOf(
                "auto" to "Auto",
                "background" to "Hidden",
                "onscreen" to "This screen",
                "bridge" to "PC",
            ),
            selected = state.settings.screenPath,
            onSelect = onScreenPath,
        )
        Text(
            "Auto tries hidden, then the PC, then this screen, and takes the first " +
                "that works. A forced choice is used alone.",
            style = MaterialTheme.typography.bodySmall,
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsGuideRow(
                "Hidden screen",
                "Apps run where you cannot see them; your phone stays put. " +
                    "Needs the helper and phone access.",
            )
            SettingsGuideRow(
                "PC",
                "The computer drives this phone over adb. Needs the bridge and its code.",
            )
            SettingsGuideRow(
                "This screen",
                "HOPA works on your screen, so you watch every tap.",
            )
        }

        Button(onClick = onRunCheck, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) "Checking\u2026" else "Run a check")
        }
        if (state.screenCheck.isEmpty()) {
            Text(
                "Reads phone access, opens Settings on the hidden screen, reads it " +
                    "back and closes it. Nothing is left open.",
                style = MaterialTheme.typography.bodySmall,
                color = HopaColor.TextHint,
            )
        } else {
            state.screenCheck.forEach { line ->
                Text("\u2022 $line", fontSize = 13.sp, color = HopaColor.TextSecondary)
            }
        }

        Text("Get the hidden screen ready", fontSize = 15.sp, color = HopaColor.TextPrimary)
        Button(
            onClick = onStartBackgroundHelper,
            enabled = !state.busy && state.settings.bridgeUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.busy) "Working\u2026" else "Start through the PC bridge")
        }
        Text(
            if (state.settings.bridgeUrl.isBlank()) {
                "Needs the PC bridge paired first."
            } else {
                "Press once after a phone reboot."
            },
            style = MaterialTheme.typography.bodySmall,
            color = HopaColor.TextHint,
        )
        state.bridgeStatus?.let { message ->
            Text(message, fontSize = 13.sp, color = HopaColor.TextSecondary)
        }

        Text("Or pair without a PC", fontSize = 15.sp, color = HopaColor.TextPrimary)
        Text(
            "Developer options, Wireless debugging, Pair device with pairing code. " +
                "Type the address and the six digits here.",
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
            enabled = !state.busy && wirelessAddress.isNotBlank() && wirelessCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Pair this phone")
        }
        Text(
            "Both fields are needed before the button turns on.",
            style = MaterialTheme.typography.bodySmall,
            color = HopaColor.TextHint,
        )
    }
}

package com.hopa.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * Where a run happens: the hidden background screen, the paired PC, or the
 * phone's own screen. Every button reports under itself, because a silent button
 * is indistinguishable from a broken one.
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, title) in listOf(
                "auto" to "Auto",
                "background" to "Background",
                "onscreen" to "This screen",
                "bridge" to "PC bridge",
            )) {
                val chosen = state.settings.screenPath == value
                Text(
                    title,
                    fontSize = 12.sp,
                    color = if (chosen) HopaColor.OnPrimary else HopaColor.TextSecondary,
                    modifier = Modifier
                        .background(
                            if (chosen) HopaColor.Primary else HopaColor.SurfaceAlt,
                            RoundedCornerShape(16.dp),
                        )
                        .clickable { onScreenPath(value) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Text(
            "Auto tries the hidden screen first, then the PC bridge, then this screen. " +
                "A forced choice is used alone.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "The hidden screen needs the helper running and phone access on, " +
                "because its tree is read through accessibility.",
            style = MaterialTheme.typography.bodySmall,
            color = HopaColor.TextHint,
        )

        Text("What each choice means", fontSize = 14.sp, color = HopaColor.TextPrimary)
        Text(
            "Background - apps open on a screen you cannot see; your phone stays " +
                "where it is. Needs the helper running.\n" +
                "PC bridge - the PC drives this phone over adb; needs the bridge " +
                "running on the PC and its pair code.\n" +
                "This screen - HOPA works on your own screen and you watch it tap.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "After a run the chat says which one was used (\"Ran on \u2026\"). " +
                "Auto falls back to the next choice that works and reports that one.",
            style = MaterialTheme.typography.bodySmall,
            color = HopaColor.TextHint,
        )

        Button(onClick = onRunCheck, enabled = !state.busy) {
            Text(if (state.busy) "Checking\u2026" else "Run a check")
        }
        Text(
            "The check reads phone access, opens Settings on the hidden screen, " +
                "reads it back and closes it again.",
            style = MaterialTheme.typography.bodySmall,
            color = HopaColor.TextHint,
        )
        state.screenCheck.forEach { line ->
            Text("\u2022 $line", fontSize = 12.sp, color = HopaColor.TextSecondary)
        }

        Text("Get the hidden screen ready", fontSize = 14.sp, color = HopaColor.TextPrimary)
        if (state.settings.bridgeUrl.isBlank()) {
            Text(
                "With a PC: pair the bridge first, then this button appears.",
                style = MaterialTheme.typography.bodySmall,
                color = HopaColor.TextHint,
            )
        }
        Button(
            onClick = onStartBackgroundHelper,
            enabled = !state.busy && state.settings.bridgeUrl.isNotBlank(),
        ) {
            Text(if (state.busy) "Working\u2026" else "Start through the PC bridge")
        }
        state.bridgeStatus?.let { message ->
            Text(message, fontSize = 12.sp, color = HopaColor.TextSecondary)
        }

        Text("Pair without a PC", fontSize = 14.sp, color = HopaColor.TextPrimary)
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
        ) {
            Text("Pair this phone")
        }
        if (wirelessAddress.isBlank() || wirelessCode.isBlank()) {
            Text(
                "Fill both fields first; the button stays off until then.",
                fontSize = 11.sp,
                color = HopaColor.TextHint,
            )
        }
    }
}

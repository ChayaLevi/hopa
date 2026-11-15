package com.hopa.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor

/**
 * The one place the background mode gets set up: the two Android steps, the
 * address and the code. A dialog, because the steps below the page were invisible
 * after tapping the fix.
 */
@Composable
fun BackgroundSetupDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConnect: (String, String) -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Turn on background mode", fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "1. Open Developer options in Android settings.\n" +
                        "2. Wireless debugging, then \"Pair device with pairing code\".\n" +
                        "3. Type the address and the six digits here.",
                    fontSize = 13.sp,
                    color = HopaColor.TextSecondary,
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address, e.g. 192.168.1.5:37123") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Six digits") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConnect(address, code) },
                enabled = !busy && address.isNotBlank() && code.isNotBlank(),
            ) {
                Text(if (busy) "Connecting\u2026" else "Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

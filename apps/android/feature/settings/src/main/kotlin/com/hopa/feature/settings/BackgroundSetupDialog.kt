package com.hopa.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.hopa.ui.theme.HopaColor

/**
 * The one place the hidden screen gets set up: the one-tap pairing, then the
 * manual address and code for a phone where the screen cannot be read.
 */
@Composable
fun BackgroundSetupDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConnect: (String, String) -> Unit,
    onPairAutomatically: () -> Unit,
    onWaitForDialog: () -> Unit,
) {
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hidden screen setup", fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "HOPA opens apps on a screen you can't see. Android doesn't " +
                        "give apps this access directly, so HOPA uses Wireless debugging " +
                        "for shell access. Screen access has to be on.",
                    fontSize = 13.sp,
                    color = HopaColor.TextSecondary,
                )
                Button(
                    onClick = onPairAutomatically,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Pairing\u2026" else "Pair this phone")
                }
                TextButton(
                    onClick = onWaitForDialog,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("I'll open the dialog myself")
                }
                Text(
                    "Or type what the dialog shows:",
                    fontSize = 12.sp,
                    color = HopaColor.TextHint,
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

package com.hopa.app.feature.execution

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.feature.settings.BackgroundSetupDialog
import com.hopa.app.feature.settings.SettingsUiState
import com.hopa.app.ui.component.HopaPageHeader
import com.hopa.ui.theme.HopaColor

/** What a run needs, grouped by the machine that provides it. */
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
    var hiddenMenu by rememberSaveable { mutableStateOf(false) }
    val bridgeReady = state.settings.bridgeUrl.isNotBlank() && state.bridgeKey.isNotBlank()
    val openHiddenSetup: () -> Unit = { setup = true }
    val openHiddenMenu: () -> Unit = { hiddenMenu = true }

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GroupLabel("This phone")
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
                            !state.settings.accessibilityAllowed -> "Paused by you"
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
                        when {
                            state.backgroundRunning -> openHiddenMenu
                            bridgeReady -> onStartBackgroundHelper
                            else -> openHiddenSetup
                        },
                    )
                }
            }
            Text(
                if (state.backgroundRunning) {
                    "Hidden screen is already set up. Tap it to re-pair or change the connection."
                } else {
                    "The hidden screen also needs screen access."
                },
                fontSize = 12.sp,
                color = HopaColor.TextHint,
            )

            GroupLabel("My computer")
            Surface(
                color = HopaColor.Surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, HopaColor.Divider),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SetupRow(
                    "Bridge",
                    if (bridgeReady) "Paired" else "Not set up",
                    bridgeReady,
                    onOpenComputer,
                )
            }
            Text(
                "The HOPA bridge on your computer sends the taps to this phone. " +
                    "Apps stay on this phone.",
                fontSize = 12.sp,
                color = HopaColor.TextHint,
            )

            Button(
                onClick = onRunCheck,
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(if (state.busy) "Checking\u2026" else "Check that it works")
            }
            if (state.screenCheck.isNotEmpty()) {
                Text(
                    "Green means ready. Grey is information, not a pass or a failure.",
                    fontSize = 11.sp,
                    color = HopaColor.TextHint,
                )
            }
            val openSetup: () -> Unit = { setup = true }
            state.screenCheck.take(8).forEach { check ->
                val actionable = !check.info
                CheckLine(
                    text = check.text,
                    ok = check.ok,
                    info = check.info,
                    action = check.action.takeIf { actionable && it.isNotBlank() },
                    onClick = if (actionable) {
                        when (check.id) {
                            "screen" -> onOpenScreenAccess
                            "background" -> openSetup
                            "computer" -> onOpenComputer
                            else -> null
                        }
                    } else {
                        null
                    },
                )
            }
            state.bridgeStatus?.let { message ->
                Text(
                    message,
                    fontSize = 12.sp,
                    color = if (message.startsWith("Bridge ok") ||
                        message.startsWith("Bridge paired") ||
                        message.contains("is ready", ignoreCase = true)
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

    if (hiddenMenu) {
        AlertDialog(
            onDismissRequest = { hiddenMenu = false },
            title = { Text("Hidden screen", fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Running", fontSize = 13.sp, color = HopaColor.Success)
                    Text(
                        "It is already set up. Pair again only if you want to change " +
                            "the connection.",
                        fontSize = 13.sp,
                        color = HopaColor.TextSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        hiddenMenu = false
                        setup = true
                    },
                ) {
                    Text("Re-pair")
                }
            },
            dismissButton = {
                TextButton(onClick = { hiddenMenu = false }) { Text("Close") }
            },
        )
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
private fun SetupRow(
    title: String,
    value: String,
    ok: Boolean,
    onClick: (() -> Unit)?,
) {
    val click = onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(click)
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
        if (onClick != null) {
            Text(
                "\u203A",
                fontSize = 16.sp,
                color = HopaColor.TextHint,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun CheckLine(
    text: String,
    ok: Boolean,
    info: Boolean = false,
    action: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val click = if (info) {
        Modifier
    } else {
        onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(click)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                info -> "\u2022"
                ok -> "\u2713"
                else -> "\u2717"
            },
            fontSize = 12.sp,
            color = when {
                info -> HopaColor.TextHint
                ok -> HopaColor.Success
                else -> HopaColor.Warning
            },
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text,
            fontSize = 12.sp,
            color = if (info) HopaColor.TextHint else HopaColor.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        if (!info && action != null && onClick != null) {
            Text(
                action,
                fontSize = 12.sp,
                color = HopaColor.Primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 11.sp,
        color = HopaColor.TextHint,
        modifier = Modifier.padding(top = 2.dp),
    )
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

package com.hopa.app.feature.settings

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor

/** Notifications: whether an answer outside the app is announced. */
@Composable
fun SettingsNotificationsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSave: (String, String, String, String, Boolean, Boolean, String, String, Boolean) -> Unit,
    onTestNotification: () -> Unit,
) {
    var notify by rememberSaveable { mutableStateOf(state.settings.notifyAnswers) }

    LaunchedEffect(state.settings) {
        notify = state.settings.notifyAnswers
    }

    SettingsPage(title = "Alerts", onBack = onBack) {
        SettingsSwitchRow("Notify when an answer is ready", notify) { notify = it }
        Button(
            onClick = {
                onSave(
                    state.settings.provider,
                    state.settings.endpoint,
                    state.settings.model,
                    "",
                    notify,
                    state.settings.webEnabled,
                    state.settings.bridgeUrl,
                    "",
                    state.settings.useBridge,
                )
            },
            enabled = !state.busy,
        ) {
            Text(if (state.busy) "Saving\u2026" else "Save")
        }
        Button(onClick = onTestNotification) {
            Text("Send a test notification")
        }
        state.notice?.let { message ->
            Text(message, fontSize = 12.sp, color = HopaColor.TextSecondary)
        }
        Text(
            "Answers that arrive while another app is in front are announced here.",
            fontSize = 12.sp,
            color = HopaColor.TextHint,
        )
    }
}

package com.hopa.feature.settings

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import com.hopa.ui.theme.HopaColor
import com.hopa.ui.settings.SettingsPage

/**
 * Alerts: the test button and the explanation. The switch itself lives on the
 * Settings hub, so this page does not keep a second copy of the same value.
 */
@Composable
fun SettingsNotificationsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onTestNotification: () -> Unit,
) {
    SettingsPage(title = "Alerts", onBack = onBack) {
        Text(
            if (state.settings.notifyAnswers) {
                "Alerts are on. Answers that arrive while another app is in front " +
                    "are announced here."
            } else {
                "Alerts are off. Turn them on with the switch on the Settings page."
            },
            fontSize = 13.sp,
            color = HopaColor.TextSecondary,
        )
        Button(onClick = onTestNotification) {
            Text("Send a test notification")
        }
        state.notice?.let { message ->
            Text(message, fontSize = 12.sp, color = HopaColor.TextSecondary)
        }
    }
}

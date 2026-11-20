package com.hopa.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.privacy.TurnLogEntry
import com.hopa.ui.theme.HopaColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.hopa.ui.settings.SettingsPage
import com.hopa.ui.settings.SettingsPanel

/**
 * What left the phone, per turn: which surface, how many steps, characters and
 * tokens. No screen text is stored here, only sizes - the promise, readable.
 */
@Composable
fun SettingsPrivacyScreen(
    entries: List<TurnLogEntry>,
    onBack: () -> Unit,
) {
    SettingsPage(title = "What left your phone", onBack = onBack) {
        Text(
            "Each turn lists how much text and how many tokens were sent, and where " +
                "it ran. Screen text is never kept here.",
            style = MaterialTheme.typography.bodySmall,
            color = HopaColor.TextHint,
        )
        if (entries.isEmpty()) {
            Text("Nothing has been sent yet.", fontSize = 13.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            entries.forEach { entry ->
                SettingsPanel {
                    Text(
                        entry.goal.ifBlank { "(no words)" },
                        fontSize = 14.sp,
                        color = HopaColor.TextPrimary,
                    )
                    Text(
                        clock(entry.at) + " - " + where(entry.path) + " - " +
                            "${entry.steps} steps, ${entry.charactersSent} characters, " +
                            "${entry.tokens} tokens",
                        fontSize = 12.sp,
                        color = HopaColor.TextSecondary,
                    )
                }
            }
        }
    }
}

private fun clock(at: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))

private fun where(path: String): String = when (path) {
    "background" -> "hidden screen"
    "bridge" -> "your computer"
    "accessibility" -> "your screen"
    else -> "no screen"
}

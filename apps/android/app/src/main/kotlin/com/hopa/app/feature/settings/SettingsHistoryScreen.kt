package com.hopa.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor
import com.hopa.data.database.entity.RunHistoryEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Every finished turn with the surface it used, and a filter by surface. */
@Composable
fun SettingsHistoryScreen(
    rows: List<RunHistoryEntity>,
    selected: String,
    onFilter: (String) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
) {
    SettingsPage(title = "Run history", onBack = onBack) {
        Text(
            "Each turn, where it ran and what it cost. Use this to compare modes: " +
                "the same request on the hidden screen and on your screen.",
            style = MaterialTheme.typography.bodySmall,
            color = HopaColor.TextHint,
        )
        SettingsSegment(
            options = listOf(
                "" to "All",
                "background" to "Hidden",
                "accessibility" to "Your screen",
                "bridge" to "My computer",
            ),
            selected = selected,
            onSelect = onFilter,
        )
        if (rows.isEmpty()) {
            Text("Nothing yet.", fontSize = 13.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { row ->
                SettingsPanel {
                    Text(row.goal, fontSize = 14.sp, color = HopaColor.TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            clock(row.at) + " - " + where(row.path) + " - " +
                                "${row.steps} steps - ${row.tokens} tokens",
                            fontSize = 12.sp,
                            color = HopaColor.TextSecondary,
                        )
                    }
                }
            }
        }
        TextButton(onClick = onClear) { Text("Clear history", fontSize = 12.sp) }
    }
}

private fun clock(at: Long): String =
    SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(at))

private fun where(path: String): String = when (path) {
    "background" -> "hidden screen"
    "bridge" -> "your computer"
    "accessibility" -> "your screen"
    else -> "no screen"
}

package com.hopa.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor

/** One self-check result, with a light that says whether that step is fine. */
@Composable
fun CheckLine(line: String) {
    val lower = line.lowercase()
    val state = when {
        lower.contains("(optional)") -> DotState.Good
        lower.contains(" off") || lower.contains("not ") || lower.contains("no ") ||
            lower.contains("could not") || lower.contains("fail") -> DotState.Attention
        else -> DotState.Good
    }
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(state, modifier = Modifier.padding(top = 6.dp))
        Text(line, fontSize = 13.sp, color = HopaColor.TextSecondary)
    }
}

package com.hopa.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.ui.theme.HopaColor

/**
 * One self-check line. A failed line always names the action that fixes it and
 * carries that action as a button - never "one step" with no step.
 */
@Composable
fun CheckRow(check: ScreenCheck, onFix: (ScreenCheck) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = check.action.isNotEmpty()) { onFix(check) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(if (check.ok) DotState.Good else DotState.Attention)
        Text(
            check.text,
            fontSize = 13.sp,
            color = HopaColor.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        if (check.action.isNotEmpty()) {
            Text(
                check.action,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = HopaColor.Primary,
            )
        }
    }
}

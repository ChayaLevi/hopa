package com.hopa.app.feature.settings

import androidx.compose.foundation.clickable
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

/** One fact in the hero panel: what it is, whether it is true, and a way in. */
@Composable
fun StatusLine(
    title: String,
    value: String,
    ok: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(if (ok) DotState.Good else DotState.Attention)
        Text(title, fontSize = 14.sp, color = HopaColor.TextPrimary)
        Text("\u00b7", fontSize = 13.sp, color = HopaColor.TextHint)
        Text(value, fontSize = 13.sp, color = HopaColor.TextSecondary)
    }
}

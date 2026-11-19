package com.hopa.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.ui.theme.HopaColor

/** A small pill action, the way a modern settings page offers one-tap fixes. */
@Composable
fun QuickChip(label: String, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .background(HopaColor.SurfaceAlt, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        fontSize = 13.sp,
        color = HopaColor.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

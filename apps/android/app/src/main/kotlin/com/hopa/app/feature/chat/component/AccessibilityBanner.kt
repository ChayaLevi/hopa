package com.hopa.app.feature.chat.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor

/**
 * Phone access is the only way HOPA can see and tap other apps. When it is off,
 * the composer still works but every run fails, so the reason and the one tap
 * that fixes it belong above the field, not inside a model answer.
 */
@Composable
fun AccessibilityBanner(
    onOpenSettings: () -> Unit,
    enabled: Boolean,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSettings),
        color = HopaColor.SurfaceAlt,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when {
                    !enabled -> "Phone access is off, so HOPA cannot see or tap apps."
                    !connected -> "Phone access is on but not connected; reopen HOPA if this stays."
                    else -> ""
                },
                fontSize = 12.sp,
                color = HopaColor.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (enabled) "Check" else "Turn on",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = HopaColor.Primary,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

package com.hopa.feature.chat.component

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
import com.hopa.ui.theme.HopaColor

/**
 * Screen access is the local way to see and tap other apps. The hidden screen
 * needs it too, to read the app's tree; only the computer does not. The banner
 * opens Act, where the person sees whether the Android service or HOPA's own
 * switch is the missing piece.
 */
@Composable
fun AccessibilityBanner(
    onOpenAct: () -> Unit,
    allowed: Boolean,
    enabled: Boolean,
    connected: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenAct),
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
                    !allowed -> "Screen access is paused in HOPA. Open Act to resume it."
                    !enabled -> "Screen access is off. Open Act to turn it on."
                    !connected -> "Screen access is on but not connected; open Act to fix it."
                    else -> ""
                },
                fontSize = 12.sp,
                color = HopaColor.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Open Act",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = HopaColor.Primary,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

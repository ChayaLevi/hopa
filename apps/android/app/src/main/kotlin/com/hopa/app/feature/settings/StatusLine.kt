package com.hopa.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import com.hopa.ui.theme.HopaColor

/**
 * One fact in the hero panel. It is a row you can open: full width, a minimum
 * touch height, a pressed wash and a chevron, because plain text is not an affordance.
 */
@Composable
fun StatusLine(
    title: String,
    value: String,
    ok: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .background(
                if (pressed) HopaColor.SurfaceMuted else androidx.compose.ui.graphics.Color.Transparent,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(if (ok) DotState.Good else DotState.Attention)
        Text(
            title,
            fontSize = 15.sp,
            color = HopaColor.TextPrimary,
            modifier = Modifier.padding(start = 8.dp),
        )
        Text("\u00b7", fontSize = 13.sp, color = HopaColor.TextHint, modifier = Modifier.padding(horizontal = 6.dp))
        Text(value, fontSize = 13.sp, color = HopaColor.TextSecondary, modifier = Modifier.weight(1f))
        Text("\u203a", fontSize = 20.sp, color = HopaColor.TextHint)
    }
}

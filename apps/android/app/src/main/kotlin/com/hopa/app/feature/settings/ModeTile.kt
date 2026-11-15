package com.hopa.app.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor

/** One mode as a small tile: a drawn glyph and one word. */
@Composable
fun ModeTile(
    mode: String,
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(if (selected) 1f else 0.55f, label = "glyph-alpha")
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (selected) HopaColor.PrimarySoft else HopaColor.Surface,
                RoundedCornerShape(16.dp),
            )
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) HopaColor.Primary else HopaColor.Surface,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onSelect)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ModeGlyph(
            mode = mode,
            tint = if (selected) HopaColor.Primary else HopaColor.TextSecondary.copy(alpha = alpha),
        )
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) HopaColor.Primary else HopaColor.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

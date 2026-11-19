package com.hopa.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.ui.theme.HopaColor

/**
 * A mode card: what it does in one line, and when it is the chosen one, how to
 * use it in plain words. Nobody should have to infer the steps.
 */
@Composable
fun SettingsChoiceCard(
    title: String,
    description: String,
    howTo: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) HopaColor.PrimarySoft else HopaColor.Surface,
                RoundedCornerShape(18.dp),
            )
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) HopaColor.Primary else HopaColor.Surface,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = HopaColor.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(HopaColor.Primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = HopaColor.OnPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(description, fontSize = 13.sp, color = HopaColor.TextSecondary)
        if (selected) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                thickness = 0.6.dp,
                color = HopaColor.Divider,
            )
            Text("How to use it", fontSize = 12.sp, color = HopaColor.TextHint)
            Text(howTo, fontSize = 13.sp, color = HopaColor.TextPrimary)
        }
    }
}

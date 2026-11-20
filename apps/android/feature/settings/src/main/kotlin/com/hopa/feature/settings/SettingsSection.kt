package com.hopa.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.ui.theme.HopaColor

/** A section heading on a settings page. */
@Composable
fun SettingsSection(title: String) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
        color = HopaColor.TextSecondary,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

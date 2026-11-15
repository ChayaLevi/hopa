package com.hopa.app.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor

/** A section heading on a settings page. */
@Composable
fun SettingsSection(title: String) {
    Text(
        title.uppercase(),
        fontSize = 11.sp,
        letterSpacing = 0.8.sp,
        color = HopaColor.TextHint,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

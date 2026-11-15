package com.hopa.app.feature.settings

import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopa.app.ui.theme.HopaColor

/** The hairline between two rows of one settings group. */
@Composable
fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 0.6.dp,
        color = HopaColor.Divider,
    )
}

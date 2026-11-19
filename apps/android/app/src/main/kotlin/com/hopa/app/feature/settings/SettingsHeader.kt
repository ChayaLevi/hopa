package com.hopa.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.ui.theme.HopaColor

/** A large page title with one line that says how things stand. */
@Composable
fun SettingsHeader(title: String, summary: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Text(
            title,
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            color = HopaColor.TextPrimary,
        )
        Text(
            summary,
            fontSize = 14.sp,
            color = HopaColor.TextSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

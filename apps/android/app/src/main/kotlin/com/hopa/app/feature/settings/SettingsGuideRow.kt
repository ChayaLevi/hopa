package com.hopa.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor

/** One explained option: a short name and what it does, as a quiet card. */
@Composable
fun SettingsGuideRow(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HopaColor.Surface, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(title, fontSize = 14.sp, color = HopaColor.TextPrimary)
        Text(
            description,
            fontSize = 13.sp,
            color = HopaColor.TextSecondary,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

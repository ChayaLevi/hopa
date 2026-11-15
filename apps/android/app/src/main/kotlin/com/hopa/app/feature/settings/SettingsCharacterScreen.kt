package com.hopa.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor

/** How HOPA talks: tone and length, plus the profiles entry. */
@Composable
fun SettingsCharacterScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onTone: (String) -> Unit,
    onChattiness: (String) -> Unit,
    onOpenProfiles: () -> Unit,
) {
    SettingsPage(title = "Character", onBack = onBack) {
        Text("Tone", fontSize = 12.sp, color = HopaColor.TextHint)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (value in listOf("friendly", "direct")) {
                val chosen = state.settings.tone == value
                Text(
                    value,
                    fontSize = 12.sp,
                    color = if (chosen) HopaColor.OnPrimary else HopaColor.TextSecondary,
                    modifier = Modifier
                        .background(
                            if (chosen) HopaColor.Primary else HopaColor.SurfaceAlt,
                            RoundedCornerShape(16.dp),
                        )
                        .clickable { onTone(value) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        Text("Length", fontSize = 12.sp, color = HopaColor.TextHint)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (value in listOf("short", "balanced", "detailed")) {
                val chosen = state.settings.chattiness == value
                Text(
                    value,
                    fontSize = 12.sp,
                    color = if (chosen) HopaColor.OnPrimary else HopaColor.TextSecondary,
                    modifier = Modifier
                        .background(
                            if (chosen) HopaColor.Primary else HopaColor.SurfaceAlt,
                            RoundedCornerShape(16.dp),
                        )
                        .clickable { onChattiness(value) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
        SettingsRow("Profiles", "Everyday and other modes", onOpenProfiles)
    }
}

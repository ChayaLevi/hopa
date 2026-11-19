package com.hopa.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.ui.theme.HopaColor

/** How HOPA talks: tone and length, plus the profiles entry. */
@Composable
fun SettingsCharacterScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onTone: (String) -> Unit,
    onChattiness: (String) -> Unit,
    onMascot: (Boolean) -> Unit,
    onOpenProfiles: () -> Unit,
) {
    SettingsPage(title = "Tone and length", onBack = onBack) {
        Text("Tone", fontSize = 13.sp, color = HopaColor.TextHint)
        SettingsSegment(
            options = listOf("friendly" to "Friendly", "direct" to "Direct"),
            selected = state.settings.tone,
            onSelect = onTone,
        )
        Text("Length", fontSize = 13.sp, color = HopaColor.TextHint)
        SettingsSegment(
            options = listOf(
                "short" to "Short",
                "balanced" to "Balanced",
                "detailed" to "Detailed",
            ),
            selected = state.settings.chattiness,
            onSelect = onChattiness,
        )
        Text("Mascot", fontSize = 13.sp, color = HopaColor.TextHint)
        SettingsSegment(
            options = listOf("framed" to "Framed", "cutout" to "Cut-out"),
            selected = if (state.settings.mascotFramed) "framed" else "cutout",
            onSelect = { onMascot(it == "framed") },
        )
        SettingsRow(
            "Profiles",
            "Everyday and other modes",
            Icons.Filled.Person,
            onOpenProfiles,
        )
    }
}

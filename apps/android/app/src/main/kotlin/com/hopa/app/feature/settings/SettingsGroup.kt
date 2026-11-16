package com.hopa.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopa.app.ui.theme.HopaColor

/**
 * A grouped list container: one rounded card per section, rows separated by a
 * hairline instead of a card each. This is the shape every 2026 settings screen
 * uses, and it stops five rows from reading as five unrelated things.
 */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = HopaColor.Surface,
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(content = content)
    }
}

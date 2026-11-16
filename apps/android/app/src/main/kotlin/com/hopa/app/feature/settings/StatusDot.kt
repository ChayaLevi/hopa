package com.hopa.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopa.app.ui.theme.HopaColor

/** A small state light: green works, amber needs a step, red is off or broken. */
@Composable
fun StatusDot(state: DotState, modifier: Modifier = Modifier) {
    val color = when (state) {
        DotState.Good -> HopaColor.Success
        DotState.Attention -> HopaColor.Warning
        DotState.Off -> HopaColor.Danger
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .background(color, CircleShape),
    )
}

package com.hopa.feature.chat.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hopa.ui.component.HopaMascot
import com.hopa.ui.theme.HopaColor

/**
 * The answer is still being worked out. A run can take seconds (a launch, a
 * screen read, another model turn), and a chat with no sign of life looks broken.
 */
@Composable
fun ThinkingIndicator(
    modifier: Modifier = Modifier,
    label: String = "",
) {
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            seconds += 1
        }
    }
    val transition = rememberInfiniteTransition(label = "thinking")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Restart,
        ),
        label = "thinking-phase",
    )
    Row(
        modifier = modifier.padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HopaMascot(sizeDp = 20.dp, interactive = false)
        Column {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (index in 0 until 3) {
                    val offset = (phase.value + index * 0.25f) % 1f
                    val alpha = 0.25f + 0.75f * (1f - kotlin.math.abs(offset * 2f - 1f))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(HopaColor.TextHint.copy(alpha = alpha), CircleShape),
                    )
                }
            }
            if (label.isNotBlank()) {
                Text(
                    "$label  ·  %d:%02d".format(seconds / 60, seconds % 60),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = HopaColor.TextHint,
                )
            }
        }
    }
}

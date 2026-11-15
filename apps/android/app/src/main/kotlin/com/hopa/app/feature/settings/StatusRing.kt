package com.hopa.app.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor

/**
 * The readiness ring: how many of the few things HOPA needs are actually true.
 * A number in the middle, an arc that grows, nothing else.
 */
@Composable
fun StatusRing(ready: Int, total: Int, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = if (total == 0) 0f else ready.toFloat() / total,
        animationSpec = tween(durationMillis = 700),
        label = "readiness",
    )
    Box(
        modifier = modifier.size(96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(96.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = HopaColor.PrimarySoft,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = HopaColor.Primary,
                startAngle = 135f,
                sweepAngle = 270f * progress,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$ready/$total",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = HopaColor.TextPrimary,
            )
            Text(if (ready == total) "ready" else "to go", fontSize = 11.sp, color = HopaColor.TextHint)
        }
    }
}

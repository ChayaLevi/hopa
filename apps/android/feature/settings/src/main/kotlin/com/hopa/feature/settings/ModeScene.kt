package com.hopa.feature.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hopa.ui.theme.HopaColor

/**
 * The scene: where the window of the app you asked for actually appears. A phone
 * for "your screen", a window beside it for the "hidden screen", a laptop for the
 * computer, both for automatic. Switching modes cross-fades between the two.
 */
@Composable
fun ModeScene(mode: String) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(HopaColor.SurfaceAlt, RoundedCornerShape(18.dp))
            .padding(8.dp),
    ) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        val windowSize = Size(w * 0.13f, h * 0.13f)

        fun phone(left: Float, top: Float, width: Float, height: Float) {
            drawRoundRect(
                color = HopaColor.TextSecondary,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(width * 0.18f),
                style = Stroke(width = stroke),
            )
        }

        fun window(left: Float, top: Float, alpha: Float = 1f) {
            drawRoundRect(
                color = HopaColor.Primary.copy(alpha = alpha),
                topLeft = Offset(left, top),
                size = windowSize,
                cornerRadius = CornerRadius(windowSize.width * 0.22f),
            )
        }

        // A frame stands in for the person's phone in every scene.
        phone(w * 0.30f, h * 0.08f, w * 0.22f, h * 0.84f)

        when (mode) {
            "onscreen" -> window(w * 0.355f, h * 0.40f)
            "background" -> window(w * 0.08f, h * 0.40f, alpha = 0.55f)
            "bridge" -> {
                val path = Path().apply {
                    moveTo(w * 0.52f, h * 0.62f)
                    lineTo(w * 0.92f, h * 0.62f)
                    lineTo(w * 0.86f, h * 0.82f)
                    lineTo(w * 0.58f, h * 0.82f)
                    close()
                }
                drawPath(path, color = HopaColor.TextSecondary, style = Stroke(width = stroke))
                drawRoundRect(
                    color = HopaColor.TextSecondary,
                    topLeft = Offset(w * 0.60f, h * 0.20f),
                    size = Size(w * 0.24f, h * 0.40f),
                    cornerRadius = CornerRadius(w * 0.02f),
                    style = Stroke(width = stroke),
                )
                window(w * 0.655f, h * 0.33f)
            }

            else -> {
                drawCircle(
                    color = HopaColor.Accent,
                    radius = w * 0.035f,
                    center = Offset(w * 0.16f, h * 0.20f),
                )
                window(w * 0.06f, h * 0.42f, alpha = 0.55f)
                window(w * 0.355f, h * 0.62f)
            }
        }
    }
}

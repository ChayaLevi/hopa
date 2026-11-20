package com.hopa.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
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
 * A 24 dp glyph for a mode, drawn from the same shapes as the scene above it:
 * a phone, a hidden window, a laptop, or both at once.
 */
@Composable
fun ModeGlyph(mode: String, tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        fun phone(left: Float, top: Float, width: Float, height: Float, filled: Boolean = false) {
            if (filled) {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(width * 0.2f),
                )
            } else {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(width * 0.2f),
                    style = Stroke(width = stroke),
                )
            }
        }
        when (mode) {
            "onscreen" -> {
                phone(w * 0.28f, h * 0.06f, w * 0.44f, h * 0.88f)
                phone(w * 0.40f, h * 0.30f, w * 0.20f, h * 0.32f, filled = true)
            }

            "background" -> {
                phone(w * 0.42f, h * 0.10f, w * 0.42f, h * 0.80f)
                phone(w * 0.12f, h * 0.34f, w * 0.22f, h * 0.28f, filled = true)
            }

            "bridge" -> {
                val path = Path().apply {
                    moveTo(w * 0.12f, h * 0.66f)
                    lineTo(w * 0.88f, h * 0.66f)
                    lineTo(w * 0.82f, h * 0.88f)
                    lineTo(w * 0.18f, h * 0.88f)
                    close()
                }
                drawPath(path, color = tint, style = Stroke(width = stroke))
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.26f, h * 0.18f),
                    size = Size(w * 0.48f, h * 0.42f),
                    cornerRadius = CornerRadius(w * 0.06f),
                    style = Stroke(width = stroke),
                )
                drawCircle(tint, radius = w * 0.07f, center = Offset(w * 0.50f, h * 0.40f))
            }

            else -> {
                phone(w * 0.10f, h * 0.14f, w * 0.34f, h * 0.72f)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.52f, h * 0.30f),
                    size = Size(w * 0.32f, h * 0.30f),
                    cornerRadius = CornerRadius(w * 0.08f),
                )
                drawCircle(HopaColor.Accent, radius = w * 0.08f, center = Offset(w * 0.80f, h * 0.22f))
            }
        }
    }
}

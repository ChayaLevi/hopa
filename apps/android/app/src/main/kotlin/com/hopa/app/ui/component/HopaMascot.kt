package com.hopa.app.ui.component

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.R
import com.hopa.app.ui.theme.HopaColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The plush HOPA. Two looks, chosen in Settings: the rounded-square badge the
 * app shipped (the default) and the cut-out object standing on the page with a
 * contact shadow. Both share the life: a 4.8 s float, breathing squash, a
 * 150 ms blink, the established thinking pulse, listening lean and happy cheer,
 * and a wiggle on tap.
 *
 * Transcript avatars pass <code>animate = false</code>, which creates no
 * infinite transitions and no blink timer, so a long chat costs nothing.
 */
@Composable
fun HopaMascot(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 92.dp,
    state: String = "idle",
    animate: Boolean = true,
    interactive: Boolean = true,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val systemMotion = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val motionOn = animate && systemMotion
    val motion = rememberIdleMotion(motionOn)
    val blinking = rememberBlink(motionOn && sizeDp >= 32.dp)
    val blinkAlpha by animateFloatAsState(
        targetValue = if (blinking) 1f else 0f,
        animationSpec = tween(durationMillis = 60),
        label = "blink",
    )
    var cheer by remember { mutableStateOf(false) }
    var pulse by remember { mutableStateOf(false) }
    var bubble by remember { mutableStateOf(false) }
    val cheerScale by animateFloatAsState(
        targetValue = if (!animate) 1f else if (cheer || state == "happy") 1.06f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "cheer",
    )
    val wiggle = remember { Animatable(0f) }
    val badge = RoundedCornerShape(sizeDp * 0.20f)
    val art = if (MascotAppearance.framed) R.drawable.hopa_plush else R.drawable.hopa_plush_cut
    val blinkArt = if (MascotAppearance.framed) {
        R.drawable.hopa_plush_blink
    } else {
        R.drawable.hopa_plush_blink_cut
    }

    LaunchedEffect(motionOn, state) {
        pulse = false
        if (!motionOn || (state != "thinking" && state != "acting")) {
            return@LaunchedEffect
        }
        while (true) {
            delay(520)
            pulse = !pulse
        }
    }

    val alpha = when {
        state == "error" -> 0.65f
        pulse -> 0.88f
        else -> 1f
    }
    val tilt = when (state) {
        "acting" -> -4f
        "listening" -> -2f
        else -> 0f
    }
    val lift = if (state == "idle" || state == "waiting") {
        sizeDp * 0.02f * motion.float
    } else {
        0.dp
    }

    Box(
        modifier = modifier
            .size(sizeDp)
            .offset(y = lift)
            .graphicsLayer(
                scaleX = cheerScale * (1f - 0.01f * motion.breathe),
                scaleY = cheerScale * (1f + 0.03f * motion.breathe),
                rotationZ = tilt + wiggle.value,
                alpha = alpha,
            ),
    ) {
        // The object look: the mascot stands on the page, so it needs a floor.
        // Two layers, the way light works: a narrow contact patch at the feet
        // and a wide, very light spread around it. The ink is warm umber, not
        // black - a neutral shadow on the cream page reads as dirt. Both shrink
        // and fade as the mascot floats up, so it stands rather than being
        // pasted on. Small avatars get none: at 26 dp it is a smudge.
        if (!MascotAppearance.framed && sizeDp >= 56.dp) {
            val ink = Color(0xFF2A1B10)
            val rise = motion.float
            val wide = sizeDp >= 72.dp
            // The plush raises its right arm, so its feet sit left of centre:
            // -5.5% is measured from the artwork's alpha, not guessed.
            val under = sizeDp * -0.055f
            if (wide) {
                SoftShadow(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(x = under, y = sizeDp * 0.045f - lift),
                    width = sizeDp * 1.05f,
                    height = sizeDp * 0.17f,
                    alpha = 0.12f * (1f - 0.15f * rise),
                    scaleX = 1f - 0.08f * rise,
                    ink = ink,
                )
            }
            SoftShadow(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(x = under, y = sizeDp * (if (wide) 0.025f else 0.03f) - lift),
                width = sizeDp * if (wide) 0.60f else 0.64f,
                height = sizeDp * if (wide) 0.085f else 0.10f,
                alpha = (if (wide) 0.22f else 0.20f) * (1f - 0.05f * rise),
                scaleX = 1f,
                ink = ink,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (MascotAppearance.framed) {
                        Modifier.shadow(elevation = 3.dp, shape = badge, clip = false).clip(badge)
                    } else {
                        Modifier
                    },
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = label(state)
                }
                .then(
                    if (interactive) {
                        Modifier.clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (motionOn) {
                                scope.launch {
                                    wiggle.animateTo(3f, tween(durationMillis = 90))
                                    wiggle.animateTo(-3f, tween(durationMillis = 90))
                                    wiggle.animateTo(0f, tween(durationMillis = 90))
                                }
                            }
                            cheer = true
                            scope.launch {
                                delay(260)
                                cheer = false
                            }
                            if (sizeDp >= 56.dp) {
                                scope.launch {
                                    delay(140)
                                    bubble = true
                                    delay(2200)
                                    bubble = false
                                }
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Image(
                painter = painterResource(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Image(
                painter = painterResource(blinkArt),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = blinkAlpha,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (bubble && sizeDp >= 56.dp) {
            Box(
                modifier = Modifier
                    .offset(x = sizeDp * 0.50f, y = -sizeDp * 1.02f)
                    .requiredWidth(sizeDp * 1.70f)
                    .requiredHeight(sizeDp * 0.99f)
                    .graphicsLayer(rotationZ = -3f),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val path = bubblePath(w, h)
                    translate(left = w * 0.01f, top = h * 0.05f) {
                        drawPath(path, color = Color(0x1A000000))
                    }
                    translate(left = w * 0.02f, top = h * 0.09f) {
                        drawPath(path, color = Color(0x0F000000))
                    }
                    drawPath(path, color = HopaColor.Accent)
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = sizeDp * 0.20f, start = sizeDp * 0.14f, end = sizeDp * 0.14f),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Text(
                        "Hi, I'm HOPA \uD83D\uDC4B",
                        color = Color.White,
                        fontSize = (
                            sizeDp.value * 0.19f /
                                LocalDensity.current.fontScale.coerceAtLeast(1f)
                            ).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * A soft ellipse of shadow. A circle is drawn and squashed by the layer, so the
 * gradient stays round while the shape does not: that keeps the edge soft on
 * all sides instead of ending in a blurred bar.
 */
@Composable
private fun SoftShadow(
    modifier: Modifier,
    width: Dp,
    height: Dp,
    alpha: Float,
    scaleX: Float,
    ink: Color,
) {
    Canvas(
        modifier = modifier
            // The layer squashes around the canvas centre, so the square has to
            // move down by half of what it loses: then the ellipse's bottom edge
            // is the element's bottom edge, and the caller decides where the
            // floor is.
            .offset(y = (width - height) / 2)
            .size(width)
            .graphicsLayer {
                this.scaleX = scaleX
                this.scaleY = height / width
            },
    ) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to ink.copy(alpha = alpha),
                    0.65f to ink.copy(alpha = alpha * 0.85f),
                    1f to Color.Transparent,
                ),
                center = center,
                radius = size.minDimension / 2f,
            ),
        )
    }
}

private data class IdleMotion(val float: Float, val breathe: Float)

@Composable
private fun rememberIdleMotion(animate: Boolean): IdleMotion {
    if (!animate) {
        return IdleMotion(0f, 0f)
    }
    val transition = rememberInfiniteTransition(label = "hopa-idle")
    val float by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "float",
    )
    val breathe by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )
    return IdleMotion(float, breathe)
}

@Composable
private fun rememberBlink(enabled: Boolean): Boolean {
    var blinking by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        blinking = false
        if (!enabled) {
            return@LaunchedEffect
        }
        while (true) {
            delay(4200)
            blinking = true
            delay(150)
            blinking = false
        }
    }
    return blinking
}

private fun label(state: String): String {
    if (state == "thinking" || state == "acting") {
        return "HOPA is working"
    }
    if (state == "waiting") {
        return "HOPA is waiting for your approval"
    }
    if (state == "listening") {
        return "HOPA is listening"
    }
    if (state == "happy") {
        return "HOPA finished"
    }
    if (state == "error") {
        return "HOPA could not finish"
    }
    return "HOPA"
}

private fun bubblePath(w: Float, h: Float): Path {
    val sx = w / 240f
    val sy = h / 108f
    fun x(value: Float) = value * sx
    fun y(value: Float) = value * sy
    return Path().apply {
        moveTo(x(36f), y(2f))
        lineTo(x(204f), y(2f))
        arcTo(Rect(x(204f), y(2f), x(238f), y(36f)), -90f, 90f, false)
        lineTo(x(238f), y(44f))
        arcTo(Rect(x(204f), y(44f), x(238f), y(78f)), 0f, 90f, false)
        lineTo(x(118f), y(78f))
        cubicTo(x(104f), y(90f), x(80f), y(100f), x(44f), y(106f))
        cubicTo(x(68f), y(94f), x(78f), y(86f), x(80f), y(78f))
        lineTo(x(36f), y(78f))
        arcTo(Rect(x(2f), y(44f), x(36f), y(78f)), 90f, 90f, false)
        lineTo(x(2f), y(36f))
        arcTo(Rect(x(2f), y(2f), x(36f), y(36f)), 180f, 90f, false)
        close()
    }
}

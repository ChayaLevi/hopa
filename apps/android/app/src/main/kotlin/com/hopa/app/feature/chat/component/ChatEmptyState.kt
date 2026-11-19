package com.hopa.app.feature.chat.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.ui.component.HopaMascot
import com.hopa.ui.theme.HopaColor
import java.util.Calendar

@Composable
fun ChatEmptyState(onQuick: (String) -> Unit) {
    var ideasOpen by remember { mutableStateOf(false) }
    val ritualTitle = ritualTitle()
    val ritualGoal = ritualGoal()
    val suggestions = listOf(
        "Open Bilibili and show me the top 3 videos",
        "Open Settings and show me Wi-Fi",
        "Draft a one-line message to my team",
    )
    // The hero keeps the middle of the pane and does not move; the examples and
    // the pills stack from the bottom, exactly as the Harmony phone does. The
    // hero sits a little above centre so even an open example list stays clear
    // of the mascot. Text here is capped at the normal system scale: this pane
    // is a fixed composition, and a large font setting made it collide.
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = (-20).dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HopaMascot(sizeDp = 76.dp)
            Text(
                text = greeting(),
                fontSize = cappedSp(13f),
                color = HopaColor.TextHint,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = "HOPA",
                style = MaterialTheme.typography.headlineLarge,
                fontSize = cappedSp(30f),
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = "Your assistant. Ask for anything.",
                fontSize = cappedSp(14.5f),
                color = HopaColor.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                text = "Payments always ask first.",
                fontSize = cappedSp(12f),
                color = HopaColor.TextHint,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
        ) {
            if (ideasOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "TRY SOMETHING",
                        fontSize = cappedSp(9f),
                        letterSpacing = 0.7.sp,
                        color = HopaColor.TextHint,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    suggestions.forEach { suggestion ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ideasOpen = false
                                    onQuick(suggestion)
                                },
                            color = HopaColor.Surface,
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 2.dp,
                        ) {
                            Text(
                                suggestion,
                                fontSize = cappedSp(12f),
                                lineHeight = cappedSp(16f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = false,
                                color = HopaColor.TextPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Pill(
                    text = if (ideasOpen) "Hide examples \u2193" else "Try an example \u2191",
                    onClick = { ideasOpen = !ideasOpen },
                )
                if (ritualTitle.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.padding(start = 8.dp),
                        onClick = { onQuick(ritualGoal) },
                        color = HopaColor.SurfaceAlt,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(
                            "$ritualTitle?",
                            fontSize = cappedSp(11.5f),
                            color = HopaColor.TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A size in sp that never grows with the system font scale. The empty state is
 * a fixed composition; letting 1.3x or larger settings through made the cards
 * wrap and cover the mascot.
 */
@Composable
private fun cappedSp(base: Float): TextUnit {
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    return (base / scale).sp
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = HopaColor.SurfaceAlt,
        shape = RoundedCornerShape(20.dp),
    ) {
        Text(
            text,
            fontSize = cappedSp(11.5f),
            color = HopaColor.Primary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private fun ritualTitle(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 5..11 -> "Morning brief"
        hour >= 18 -> "Evening close"
        else -> ""
    }
}

private fun ritualGoal(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 5..11 -> "What should I get done today?"
        hour >= 18 -> "What did I get done today?"
        else -> ""
    }
}

private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour in 5..11 -> "Good morning"
        hour in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
}

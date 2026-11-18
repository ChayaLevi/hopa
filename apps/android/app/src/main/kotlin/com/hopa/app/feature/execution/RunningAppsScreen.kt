package com.hopa.app.feature.execution

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.feature.settings.SettingsUiState
import com.hopa.app.ui.component.HopaPageHeader
import com.hopa.app.ui.theme.HopaColor

/**
 * The hub for everything HOPA can act on. The current mode gets a hero card
 * with its own small illustration; the catalogs and setup sit below as two
 * groups, so the page reads in one pass instead of four equal boxes.
 */
@Composable
fun RunningAppsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onOpenHowHopaActs: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenRunPrefs: () -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenSites: () -> Unit,
) {
    val mode = ActMode.of(state)

    Scaffold(
        containerColor = HopaColor.Bg,
        topBar = { HopaPageHeader(title = "Act", onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ModeHero(mode = mode, onClick = onOpenHowHopaActs)

            SectionLabel("Open")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ActTile(
                    title = "Apps",
                    subtitle = "Installed apps",
                    icon = Icons.Outlined.Apps,
                    onClick = onOpenAllApps,
                    modifier = Modifier.weight(1f),
                )
                ActTile(
                    title = "Sites",
                    subtitle = "Websites",
                    icon = Icons.Outlined.Language,
                    onClick = onOpenSites,
                    modifier = Modifier.weight(1f),
                )
            }

            SectionLabel("Setup")
            Surface(
                color = HopaColor.Surface,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, HopaColor.Divider),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    ActRow(
                        title = "Setup & test",
                        subtitle = setupSubtitle(mode),
                        icon = Icons.Outlined.Build,
                        onClick = onOpenSetup,
                    )
                    ActHairLine()
                    ActRow(
                        title = "Run preferences",
                        subtitle = runPreferencesLine(state),
                        icon = Icons.Outlined.Tune,
                        onClick = onOpenRunPrefs,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeHero(mode: ActMode, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(HopaColor.PrimarySoft, HopaColor.Surface),
                ),
            )
            .border(
                1.dp,
                HopaColor.Primary.copy(alpha = 0.12f),
                RoundedCornerShape(28.dp),
            )
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 34.dp, y = (-46).dp)
                .size(160.dp)
                .background(HopaColor.Primary.copy(alpha = 0.05f), CircleShape),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                ModeBadge(mode)
                Spacer(Modifier.width(14.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        "HOW HOPA ACTS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        color = HopaColor.TextHint,
                    )
                    Text(
                        if (mode.onComputer) "My computer" else "This phone",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HopaColor.TextPrimary,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    color = if (modeReady(mode)) {
                                        HopaColor.Success
                                    } else {
                                        HopaColor.Warning
                                    },
                                    shape = CircleShape,
                                ),
                        )
                        Text(
                            heroModeLine(mode),
                            fontSize = 13.sp,
                            color = HopaColor.TextSecondary,
                        )
                    }
                }
                ChangePill()
            }
            Text(
                modeNote(mode),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = HopaColor.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ModeBadge(mode: ActMode) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .background(HopaColor.Surface.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (mode.onComputer) Icons.Outlined.Laptop else Icons.Outlined.PhoneAndroid,
            contentDescription = null,
            tint = HopaColor.Primary,
            modifier = Modifier.size(26.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(20.dp)
                .background(HopaColor.Surface, CircleShape)
                .border(1.dp, HopaColor.Divider, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    mode.onComputer -> Icons.Outlined.PhoneAndroid
                    mode.hidden -> Icons.Outlined.VisibilityOff
                    else -> Icons.Outlined.Visibility
                },
                contentDescription = null,
                tint = HopaColor.Primary,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun ChangePill() {
    Surface(
        color = HopaColor.Surface.copy(alpha = 0.82f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, HopaColor.Primary.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Change",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = HopaColor.Primary,
            )
            Text(
                "\u203A",
                fontSize = 16.sp,
                color = HopaColor.Primary,
                modifier = Modifier.padding(start = 2.dp),
            )
        }
    }
}

@Composable
private fun ActTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = HopaColor.Surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, HopaColor.Divider),
        modifier = modifier
            .height(126.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(HopaColor.PrimarySoft, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = HopaColor.Primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text("\u203A", fontSize = 18.sp, color = HopaColor.TextHint)
            }
            Spacer(Modifier.weight(1f))
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = HopaColor.TextPrimary,
            )
            Text(
                subtitle,
                fontSize = 11.sp,
                color = HopaColor.TextHint,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ActRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(HopaColor.SurfaceAlt, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = HopaColor.Primary,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = HopaColor.TextPrimary,
            )
            Text(
                subtitle,
                fontSize = 11.sp,
                color = HopaColor.TextHint,
                maxLines = 2,
            )
        }
        Text("\u203A", fontSize = 18.sp, color = HopaColor.TextHint)
    }
}

@Composable
private fun ActHairLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 62.dp)
            .height(1.dp)
            .background(HopaColor.Divider),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.sp,
        color = HopaColor.TextHint,
        modifier = Modifier.padding(start = 2.dp),
    )
}

private fun modeReady(mode: ActMode): Boolean = when {
    mode.onComputer -> mode.computerReady
    mode.hidden -> mode.hiddenReady || (mode.fallback && mode.screenReady)
    else -> mode.screenReady
}

private fun heroModeLine(mode: ActMode): String = when {
    mode.onComputer && !mode.computerPaired -> "Bridge not set up"
    mode.onComputer && !mode.computerEnabled -> "Bridge switched off"
    mode.onComputer -> "HOPA bridge"
    mode.hidden -> "Hidden screen"
    else -> "Your screen"
}

private fun modeNote(mode: ActMode): String = when {
    mode.onComputer && !mode.computerPaired -> "Pair the bridge once to let it send the taps."
    mode.onComputer && !mode.computerEnabled -> "The bridge is paired but switched off."
    mode.onComputer -> "The app still opens on this phone's screen."
    mode.hidden && !mode.phoneAccess ->
        if (mode.pausedByYou) {
            "Screen access is paused by you."
        } else {
            "Screen access is off, so the hidden screen can't be read."
        }
    mode.hidden && !mode.hiddenHelper && mode.fallback && mode.screenReady ->
        "Hidden screen isn't ready; HOPA will use your screen."
    mode.hidden && !mode.hiddenHelper -> "Hidden screen isn't running."
    mode.hidden -> "HOPA works without taking over your screen."
    mode.pausedByYou -> "Screen access is paused by you."
    !mode.screenReady -> "Screen access is off."
    else -> "You watch every tap."
}

private fun setupSubtitle(mode: ActMode): String = when {
    mode.onComputer && !mode.computerPaired -> "Bridge not set up"
    mode.onComputer && !mode.computerEnabled -> "Bridge switched off"
    mode.onComputer -> "Bridge paired"
    !mode.phoneAccess && mode.pausedByYou -> "Screen access is paused"
    !mode.phoneAccess -> "Screen access is off"
    mode.hidden && !mode.hiddenHelper -> "Hidden screen isn't running"
    mode.hidden -> "Hidden screen is ready"
    else -> "Screen access is on"
}

private fun runPreferencesLine(state: SettingsUiState): String {
    val plan = if (state.settings.planFirst) "plan first" else "no plan"
    val vision = if (state.settings.vision) "screenshots" else "no screenshots"
    val replay = if (state.settings.replaySkills) "replay on" else "replay off"
    return "$plan, $vision, $replay"
}

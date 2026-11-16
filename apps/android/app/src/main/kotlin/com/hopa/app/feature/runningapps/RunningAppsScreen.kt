package com.hopa.app.feature.runningapps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.feature.settings.BackgroundSetupDialog
import com.hopa.app.feature.settings.SettingsUiState
import com.hopa.app.ui.component.AppIcon
import com.hopa.app.ui.component.HopaPageHeader
import com.hopa.app.ui.component.HopaStatusPill
import com.hopa.app.ui.theme.HopaColor
import com.hopa.core.router.RouteTarget

/**
 * The house for running other apps: one state, one primary action, the surface
 * as a single choice, the apps themselves, then what a run needs. No paragraph
 * explains a button; the button does the thing.
 */
@Composable
fun RunningAppsScreen(
    state: SettingsUiState,
    apps: List<RouteTarget>,
    onBack: () -> Unit,
    onScreenPath: (String) -> Unit,
    onStartBackgroundHelper: () -> Unit,
    onPairWireless: (String, String) -> Unit,
    onRunCheck: () -> Unit,
    onPlanFirst: (Boolean) -> Unit,
    onVision: (Boolean) -> Unit,
    onReplaySkills: (Boolean) -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenAllApps: () -> Unit,
    onOpenScreenAccess: () -> Unit,
    onOpenComputer: () -> Unit,
) {
    var setup by rememberSaveable { mutableStateOf(false) }
    val mode = state.settings.screenPath
    val bridgeReady = state.settings.bridgeUrl.isNotBlank()
    val shown = apps.take(4)
    val backgroundCheck = state.screenCheck.firstOrNull { it.id == "background" }
    val needsSetup = backgroundCheck != null && !backgroundCheck.ok && mode != "onscreen"
    val hiddenForced = mode == "background" && !state.backgroundRunning

    Scaffold(
        containerColor = HopaColor.Bg,
        topBar = {
            HopaPageHeader(
                title = "Running apps",
                onBack = onBack,
                status = {
                    HopaStatusPill(
                        text = when {
                            !state.phoneAccess -> "Needs screen access"
                            hiddenForced -> "Hidden screen is off"
                            needsSetup -> "Background off"
                            else -> "Ready"
                        },
                        color = when {
                            !state.phoneAccess -> HopaColor.Warning
                            hiddenForced -> HopaColor.Warning
                            needsSetup -> HopaColor.Warning
                            else -> HopaColor.Success
                        },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Surface(
                color = HopaColor.Surface,
                shape = RoundedCornerShape(22.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, HopaColor.Divider),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        when {
                            !state.phoneAccess -> "Screen access is off"
                            hiddenForced -> "Hidden mode needs the helper running"
                            needsSetup -> "Background mode is not running"
                            else -> "Ready to open apps"
                        },
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HopaColor.TextPrimary,
                    )
                    Text(
                        if (state.phoneAccess) {
                            surfaceSentence(mode, bridgeReady)
                        } else {
                            "HOPA cannot see or tap until you allow it."
                        },
                        fontSize = 12.sp,
                        color = HopaColor.TextHint,
                    )
                    Button(
                        onClick = {
                            if (state.phoneAccess) onRunCheck() else onOpenScreenAccess()
                        },
                        enabled = !state.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                    ) {
                        Text(
                            when {
                                !state.phoneAccess -> "Allow screen access"
                                state.busy -> "Checking\u2026"
                                else -> "Check that it works"
                            },
                        )
                    }
                    if (state.screenCheck.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            state.screenCheck.take(4).forEach { check ->
                                CheckLineRow(check.text, check.ok) {
                                    when (check.id) {
                                        "screen" -> onOpenScreenAccess()
                                        "background" -> setup = true
                                        "computer" -> onOpenComputer()
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("Apps", action = "See all", onAction = onOpenAllApps)
                Surface(
                    color = HopaColor.Surface,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HopaColor.Divider),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        if (shown.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(onClick = onOpenAllApps)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Nothing from the catalog is installed yet",
                                    fontSize = 13.sp,
                                    color = HopaColor.TextSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("\u203a", fontSize = 16.sp, color = HopaColor.TextHint)
                            }
                        } else {
                            shown.forEachIndexed { index, app ->
                                if (index > 0) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 58.dp)
                                            .height(1.dp)
                                            .background(HopaColor.Divider),
                                    )
                                }
                                MiniAppRow(app) { onOpenApp(app.name) }
                            }
                        }
                    }
                }
            }

            SurfaceChoice(mode = mode, onPick = onScreenPath)

            if (mode != "onscreen") {
                Text(
                    if (bridgeReady) {
                        "After a reboot, run this once."
                    } else {
                        "Set it up once with a code from Android's wireless debugging."
                    },
                    fontSize = 11.sp,
                    color = HopaColor.TextHint,
                )
                // Pairing runs after the dialog closes, so its answer has to land
                // somewhere visible; a silent failure leaves the code unusable.
                state.bridgeStatus?.let { message ->
                    Text(
                        message,
                        fontSize = 12.sp,
                        color = if (message.startsWith("Paired")) {
                            HopaColor.Success
                        } else {
                            HopaColor.Warning
                        },
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("What a run needs")
                Surface(
                    color = HopaColor.Surface,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HopaColor.Divider),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        InfoRow(
                            "Screen access",
                            if (state.phoneAccess) "On" else "Off",
                            state.phoneAccess,
                            onOpenScreenAccess,
                        )
                        HairLine()
                        InfoRow(
                            "Hidden screen",
                            when {
                                state.backgroundRunning -> "Running"
                                bridgeReady -> "Start"
                                else -> "Set up"
                            },
                            state.backgroundRunning,
                            {
                                if (state.backgroundRunning || bridgeReady) {
                                    onStartBackgroundHelper()
                                } else {
                                    setup = true
                                }
                            },
                        )
                        HairLine()
                        InfoRow(
                            "My computer",
                            if (bridgeReady) "Paired" else "Not set up",
                            bridgeReady,
                            onOpenComputer,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader("When it runs")
                Surface(
                    color = HopaColor.Surface,
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HopaColor.Divider),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        SwitchLine(
                            "Plan first",
                            "Ask before the first tap",
                            state.settings.planFirst,
                            onPlanFirst,
                        )
                        HairLine()
                        SwitchLine(
                            "Look at the screen",
                            "Send a screenshot with the request",
                            state.settings.vision,
                            onVision,
                        )
                        HairLine()
                        SwitchLine(
                            "Repeat what worked",
                            "Skip the model next time",
                            state.settings.replaySkills,
                            onReplaySkills,
                        )
                    }
                }
            }
        }
    }

    if (setup) {
        BackgroundSetupDialog(
            busy = state.busy,
            onDismiss = { setup = false },
            onConnect = { address, code ->
                onPairWireless(address, code)
                setup = false
            },
        )
    }
}

/** One line saying what the chosen surface will do, not four lines saying all. */
private fun surfaceSentence(mode: String, bridgeReady: Boolean): String = when (mode) {
    "background" -> "Opens apps on a screen you cannot see."
    "onscreen" -> "Uses your screen, so you see every tap."
    "bridge" -> if (bridgeReady) {
        "Your computer does the tapping."
    } else {
        "Your computer is not paired yet."
    }

    else -> "Picks hidden, then computer, then this screen."
}

@Composable
private fun SurfaceChoice(mode: String, onPick: (String) -> Unit) {
    val options = listOf(
        "auto" to "Auto",
        "background" to "Hidden",
        "onscreen" to "Here",
        "bridge" to "Computer",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HopaColor.SurfaceMuted, RoundedCornerShape(100.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { (key, label) ->
            val selected = mode == key
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .background(
                        if (selected) HopaColor.Surface else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(100.dp),
                    )
                    .border(
                        if (selected) 1.dp else 0.dp,
                        if (selected) HopaColor.Primary else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(100.dp),
                    )
                    .clickable { onPick(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = if (selected) HopaColor.Primary else HopaColor.TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = HopaColor.TextSecondary,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(action, fontSize = 13.sp, color = HopaColor.Primary)
            }
        }
    }
}

@Composable
private fun MiniAppRow(app: RouteTarget, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(
            packageName = app.bundle,
            label = app.display,
            size = 30.dp,
            corner = 9.dp,
        )
        Text(
            app.display,
            fontSize = 14.sp,
            color = HopaColor.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("\u203a", fontSize = 16.sp, color = HopaColor.TextHint)
    }
}

@Composable
private fun InfoRow(
    title: String,
    state: String,
    ok: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(if (ok) HopaColor.Success else HopaColor.Warning, CircleShape),
        )
        Text(
            title,
            fontSize = 14.sp,
            color = HopaColor.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(state, fontSize = 12.sp, color = HopaColor.TextHint)
        Text("\u203a", fontSize = 16.sp, color = HopaColor.TextHint)
    }
}

@Composable
private fun SwitchLine(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = HopaColor.TextPrimary)
            Text(detail, fontSize = 11.sp, color = HopaColor.TextHint)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CheckLineRow(text: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (ok) HopaColor.Success else HopaColor.Warning, CircleShape),
        )
        Text(text, fontSize = 12.sp, color = HopaColor.TextSecondary)
    }
}

@Composable
private fun HairLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(HopaColor.Divider),
    )
}

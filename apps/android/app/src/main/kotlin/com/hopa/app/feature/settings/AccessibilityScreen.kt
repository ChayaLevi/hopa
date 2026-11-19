package com.hopa.app.feature.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.component.HopaPageHeader
import com.hopa.app.ui.component.HopaStatusPill
import com.hopa.ui.theme.HopaColor

/**
 * Screen access: one switch, two facts, and the long explanations folded away.
 * The permission is the whole point of the page, so it is the first thing on it.
 */
@Composable
fun AccessibilityScreen(
    allowed: Boolean,
    serviceEnabled: Boolean,
    connected: Boolean,
    installedAt: String,
    onToggle: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    var privacy by rememberSaveable { mutableStateOf(false) }
    var blocked by rememberSaveable { mutableStateOf(false) }
    val live = allowed && serviceEnabled && connected

    Scaffold(
        containerColor = HopaColor.Bg,
        topBar = {
            HopaPageHeader(
                title = "Screen access",
                onBack = onBack,
                status = {
                    HopaStatusPill(
                        text = when {
                            live -> "On"
                            // The switch is the app's own kill switch: nothing is
                            // reconnecting, the person paused it here.
                            !allowed && serviceEnabled -> "Paused by you"
                            serviceEnabled && !connected -> "Connecting"
                            else -> "Off"
                        },
                        color = when {
                            live -> HopaColor.Success
                            !allowed && serviceEnabled -> HopaColor.Warning
                            serviceEnabled && !connected -> HopaColor.Warning
                            else -> HopaColor.TextHint
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                color = HopaColor.Surface,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, HopaColor.Divider),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Let HOPA see and tap",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HopaColor.TextPrimary,
                            )
                            Text(
                                "Off refuses every action, even if Android still has the service on.",
                                fontSize = 11.sp,
                                color = HopaColor.TextHint,
                            )
                        }
                        Switch(checked = allowed, onCheckedChange = onToggle)
                    }
                    if (serviceEnabled && !connected) {
                        Text(
                            "Turn the switch off and on again; if it stays like this, " +
                                "restart the phone.",
                            fontSize = 11.sp,
                            color = HopaColor.Warning,
                        )
                    }
                }
            }

            Surface(
                color = HopaColor.Surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, HopaColor.Divider),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    FactRow(
                        "Android service",
                        if (serviceEnabled) "On" else "Off",
                        serviceEnabled,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp)
                            .height(1.dp)
                            .background(HopaColor.Divider),
                    )
                    FactRow("Connection", if (connected) "Live" else "Not connected", connected)
                }
            }

            Button(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
            ) {
                Text("Open Android settings")
            }

            Fold(
                title = "What HOPA reads",
                open = privacy,
                onToggle = { privacy = !privacy },
            ) {
                Text(
                    "The visible screen text, button labels and their positions. " +
                        "Password fields are dropped, and emails, phone numbers, cards " +
                        "and one-time codes are replaced before anything leaves the phone. " +
                        "Screenshots are taken only during a run, at most once a second, " +
                        "and a screen that hides itself stays hidden.",
                    fontSize = 12.sp,
                    color = HopaColor.TextSecondary,
                )
            }

            Fold(
                title = "If Android says it is blocked",
                open = blocked,
                onToggle = { blocked = !blocked },
            ) {
                Text(
                    "Sideloaded apps need restricted settings allowed first: Settings, " +
                        "Apps, HOPA, the three-dot menu, Allow restricted settings. On " +
                        "Samsung, if that item is missing, turn off Auto Blocker under " +
                        "Security and privacy, repeat the step, then turn it back on.",
                    fontSize = 12.sp,
                    color = HopaColor.TextSecondary,
                )
            }

            Text(
                "Build $installedAt",
                fontSize = 11.sp,
                color = HopaColor.TextHint,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun FactRow(title: String, state: String, ok: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
    }
}

@Composable
private fun Fold(
    title: String,
    open: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        TextButton(onClick = onToggle, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
            Text(
                if (open) "$title  \u2039" else "$title  \u203a",
                fontSize = 13.sp,
                color = HopaColor.TextSecondary,
            )
        }
        AnimatedVisibility(visible = open) {
            Box(modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                content()
            }
        }
    }
}

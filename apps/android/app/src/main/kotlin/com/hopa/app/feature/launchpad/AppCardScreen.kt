package com.hopa.app.feature.launchpad

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.ui.component.AppIcon
import com.hopa.ui.component.HopaPageHeader
import com.hopa.ui.component.HopaStatusPill
import com.hopa.ui.theme.HopaColor
import com.hopa.catalog.routing.RouteTarget

/**
 * One app: whether it is here, how a run would reach it, and the few rules the
 * catalog keeps. The card is the decision point, so it leads with the one action
 * and hides the details below it.
 */
@Composable
fun AppCardScreen(
    target: RouteTarget?,
    surface: String,
    screenAccess: Boolean,
    trusted: Boolean,
    status: String,
    functions: List<String>,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onOpenScreenAccess: () -> Unit,
    onTrust: (Boolean) -> Unit,
) {
    val installed = target != null && target.status == "ready" && target.bundle.isNotBlank()
    Scaffold(
        containerColor = HopaColor.Bg,
        topBar = {
            HopaPageHeader(
                title = target?.display.orEmpty().ifBlank { "App" },
                onBack = onBack,
                status = target?.let {
                    {
                        HopaStatusPill(
                            text = if (installed) "Installed" else "Not installed",
                            color = if (installed) HopaColor.Success else HopaColor.Warning,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (target == null) {
            Text(
                "This app is not in the catalog any more.",
                fontSize = 13.sp,
                color = HopaColor.TextSecondary,
                modifier = Modifier
                    .padding(padding)
                    .padding(20.dp),
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AppIcon(
                    packageName = target.bundle,
                    label = target.display,
                    size = 60.dp,
                    corner = 18.dp,
                )
                Text(
                    target.purpose.ifBlank { target.name },
                    fontSize = 13.sp,
                    color = HopaColor.TextSecondary,
                    modifier = Modifier.weight(1f),
                )
            }

            Button(
                onClick = { if (installed) onOpen() else onInstall() },
                enabled = installed || target.target.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Text(
                    if (installed) "Open ${target.display}" else "Get it on Play Store",
                    fontSize = 15.sp,
                )
            }
            if (status.isNotBlank()) {
                Text(
                    status,
                    fontSize = 11.sp,
                    color = if (status.startsWith("Couldn't")) {
                        HopaColor.Danger
                    } else {
                        HopaColor.TextSecondary
                    },
                )
            }

            if (functions.isNotEmpty()) {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            "App functions",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HopaColor.TextPrimary,
                        )
                        functions.forEach { function ->
                            Text(
                                function,
                                fontSize = 13.sp,
                                color = HopaColor.TextSecondary,
                            )
                        }
                        Text(
                            "A run calls these instead of tapping while this app is open.",
                            fontSize = 11.sp,
                            color = HopaColor.TextHint,
                        )
                    }
                }
            }

            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "A run uses $surface",
                        fontSize = 14.sp,
                        color = HopaColor.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Line()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenScreenAccess)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (screenAccess) HopaColor.Success else HopaColor.Warning,
                                CircleShape,
                            ),
                    )
                    Text(
                        "Screen access",
                        fontSize = 14.sp,
                        color = HopaColor.TextPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (screenAccess) "On" else "Off",
                        fontSize = 12.sp,
                        color = HopaColor.TextHint,
                    )
                    Text("\u203a", fontSize = 16.sp, color = HopaColor.TextHint)
                }
            }

            Card {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Skip the question for this app",
                            fontSize = 14.sp,
                            color = HopaColor.TextPrimary,
                        )
                        Text(
                            "Typed calls, messages and closing it. Payments and " +
                                "uninstalling always ask.",
                            fontSize = 11.sp,
                            color = HopaColor.TextHint,
                        )
                    }
                    Switch(checked = trusted, onCheckedChange = onTrust)
                }
            }

            if (target.actions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Before it acts",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = HopaColor.TextSecondary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            target.actions.forEach { rule ->
                                Text(
                                    "\u2022  $rule",
                                    fontSize = 12.sp,
                                    color = HopaColor.TextSecondary,
                                )
                            }
                        }
                    }
                }
            }

            Text(
                "Package: ${target.target.ifBlank { "not known on Android" }}",
                fontSize = 11.sp,
                color = HopaColor.TextHint,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(
        color = HopaColor.Surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, HopaColor.Divider),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun Line() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp)
            .height(1.dp)
            .background(HopaColor.Divider),
    )
}

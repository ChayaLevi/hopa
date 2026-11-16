package com.hopa.app.feature.launchpad

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.component.HopaTopBar
import com.hopa.app.ui.theme.HopaColor
import com.hopa.core.router.RouteTarget

/**
 * One app: what it is for, whether this phone has it, how a run would reach it,
 * and the few rules the catalog keeps about it. Everything a person needs
 * before asking HOPA to touch an app lives here, not in Settings.
 */
@Composable
fun AppCardScreen(
    target: RouteTarget?,
    surface: String,
    screenAccess: Boolean,
    status: String,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
    onOpenScreenAccess: () -> Unit,
) {
    Scaffold(
        containerColor = HopaColor.Bg,
        topBar = {
            HopaTopBar {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = HopaColor.TextPrimary,
                    )
                }
                Text(
                    target?.display.orEmpty().ifBlank { "App" },
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = HopaColor.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
    ) { padding ->
        if (target == null) {
            Text(
                "This app is not in the catalog any more.",
                fontSize = 13.sp,
                color = HopaColor.TextSecondary,
                modifier = Modifier.padding(padding).padding(20.dp),
            )
            return@Scaffold
        }
        val installed = target.status == "ready" && target.bundle.isNotBlank()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(HopaColor.PrimarySoft, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        target.display.take(1).uppercase(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = HopaColor.Primary,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        target.display,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        color = HopaColor.TextPrimary,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(
                                    if (installed) HopaColor.Success else HopaColor.Warning,
                                    CircleShape,
                                ),
                        )
                        Text(
                            if (installed) "Installed" else "Not installed",
                            fontSize = 11.sp,
                            color = if (installed) HopaColor.Success else HopaColor.Warning,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }

            if (target.purpose.isNotBlank()) {
                Text(target.purpose, fontSize = 13.sp, color = HopaColor.TextSecondary)
            }

            Button(
                onClick = { if (installed) onOpen() else onInstall() },
                enabled = installed || target.target.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
            ) {
                Text(if (installed) "Open ${target.display}" else "Get it on Play Store")
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

            Text("How a run reaches it", fontSize = 13.sp, color = HopaColor.TextSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HopaColor.Surface, RoundedCornerShape(14.dp))
                    .border(1.dp, HopaColor.Divider, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Menu, contentDescription = null, tint = HopaColor.TextSecondary)
                Column(modifier = Modifier.weight(1f)) {
                    Text("Opens with $surface", fontSize = 14.sp, color = HopaColor.TextPrimary)
                    Text(
                        "Change it on Running apps.",
                        fontSize = 11.sp,
                        color = HopaColor.TextHint,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HopaColor.Surface, RoundedCornerShape(14.dp))
                    .border(1.dp, HopaColor.Divider, RoundedCornerShape(14.dp))
                    .clickable(onClick = onOpenScreenAccess)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = HopaColor.TextSecondary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (screenAccess) "Screen access is on" else "Screen access is off",
                        fontSize = 14.sp,
                        color = HopaColor.TextPrimary,
                    )
                    Text(
                        if (screenAccess) {
                            "HOPA can read this app's screen and tap in it."
                        } else {
                            "Turn it on to let a run see and tap."
                        },
                        fontSize = 11.sp,
                        color = HopaColor.TextHint,
                    )
                }
            }

            if (target.actions.isNotEmpty()) {
                Text("Before it acts", fontSize = 13.sp, color = HopaColor.TextSecondary)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    target.actions.forEach { rule ->
                        Text("\u2022  $rule", fontSize = 12.sp, color = HopaColor.TextSecondary)
                    }
                }
            }

            Text(
                "Package: ${target.target.ifBlank { "not known for Android" }}",
                fontSize = 11.sp,
                color = HopaColor.TextHint,
            )
        }
    }
}

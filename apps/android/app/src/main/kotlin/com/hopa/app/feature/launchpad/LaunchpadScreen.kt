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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.component.HopaTopBar
import com.hopa.app.ui.theme.HopaColor
import com.hopa.core.router.RouteTarget

/**
 * The catalogue as a list: apps and sites, the same two sections Harmony shows.
 * A row opens the app or the site; a dot says whether this phone can open it,
 * and a missing app offers its store page instead of doing nothing.
 */
@Composable
fun LaunchpadScreen(
    apps: List<RouteTarget>,
    sites: List<RouteTarget>,
    status: String,
    onBack: () -> Unit,
    onLaunch: (RouteTarget) -> Unit,
    onInstall: (RouteTarget) -> Unit,
    onOpenSite: (RouteTarget) -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim().lowercase()
    val shownApps = apps.filter { target -> target.matches(needle) }
    val shownSites = sites.filter { target ->
        target.matches(needle) || target.domains.any { it.lowercase().contains(needle) }
    }

    Scaffold(containerColor = HopaColor.Bg) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HopaTopBar {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = HopaColor.TextPrimary,
                    )
                }
                Text(
                    "Launchpad",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = HopaColor.TextPrimary,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(HopaColor.SurfaceMuted, RoundedCornerShape(11.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                LaunchpadSegment("Apps", apps.size, section == 0, Modifier.weight(1f)) {
                    section = 0
                }
                LaunchpadSegment("Sites", sites.size, section == 1, Modifier.weight(1f)) {
                    section = 1
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps and sites", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            if (status.isNotEmpty()) {
                Text(
                    status,
                    fontSize = 11.sp,
                    color = if (status.startsWith("Couldn't")) {
                        HopaColor.Danger
                    } else {
                        HopaColor.TextSecondary
                    },
                    maxLines = 2,
                )
            }
            val rows = if (section == 0) shownApps else shownSites
            if (rows.isEmpty()) {
                Text(
                    "Nothing matches \u201c${query.trim()}\u201d.",
                    fontSize = 12.sp,
                    color = HopaColor.TextHint,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(rows, key = { it.kind + it.name }) { target ->
                        if (target.kind == "site") {
                            LaunchpadSiteRow(target) { onOpenSite(target) }
                        } else {
                            LaunchpadAppRow(
                                target = target,
                                onOpen = { onLaunch(target) },
                                onInstall = { onInstall(target) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Apps and sites share one search: display, id, aliases and purpose. */
private fun RouteTarget.matches(needle: String): Boolean {
    if (needle.isEmpty()) {
        return true
    }
    return display.lowercase().contains(needle) ||
        name.lowercase().contains(needle) ||
        aliases.any { it.lowercase().contains(needle) } ||
        purpose.lowercase().contains(needle)
}

@Composable
private fun LaunchpadSegment(
    label: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onSelect: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .background(
                if (selected) HopaColor.Surface else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$label  $count",
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            color = if (selected) HopaColor.TextPrimary else HopaColor.TextSecondary,
        )
    }
}

@Composable
private fun LaunchpadAppRow(
    target: RouteTarget,
    onOpen: () -> Unit,
    onInstall: () -> Unit,
) {
    val ready = target.status == "ready" && target.bundle.isNotBlank()
    val word = if (ready) "Installed" else "Not installed \u00b7 Play"
    val dot = if (ready) HopaColor.Success else HopaColor.Warning
    LaunchpadRow(
        monogram = monogram(target.display.ifBlank { target.name }),
        monogramTint = HopaColor.Primary,
        monogramBg = HopaColor.PrimarySoft,
        onOpen = {
            when {
                ready -> onOpen()
                target.target.isNotBlank() -> onInstall()
            }
        },
    ) {
        Text(
            target.display.ifBlank { target.name },
            fontSize = 15.sp,
            color = HopaColor.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            target.purpose.ifBlank { target.name },
            fontSize = 11.sp,
            color = HopaColor.TextHint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dot, CircleShape),
            )
            Text(
                word,
                fontSize = 10.sp,
                color = dot,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun LaunchpadSiteRow(
    target: RouteTarget,
    onOpen: () -> Unit,
) {
    LaunchpadRow(
        monogram = monogram(target.display.ifBlank { target.name }),
        monogramTint = HopaColor.TextSecondary,
        monogramBg = HopaColor.SurfaceMuted,
        onOpen = { if (target.domains.isNotEmpty()) onOpen() },
    ) {
        Text(
            target.display.ifBlank { target.name },
            fontSize = 15.sp,
            color = HopaColor.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            target.domains.joinToString(" \u00b7 "),
            fontSize = 11.sp,
            color = HopaColor.TextHint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LaunchpadRow(
    monogram: String,
    monogramTint: Color,
    monogramBg: Color,
    onOpen: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HopaColor.Surface, RoundedCornerShape(12.dp))
            .border(1.dp, HopaColor.Divider, RoundedCornerShape(12.dp))
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(monogramBg, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                monogram,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = monogramTint,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            content()
        }
        Text("\u203a", fontSize = 17.sp, color = HopaColor.TextHint)
    }
}

private fun monogram(text: String): String =
    text.trim().take(1).uppercase().ifEmpty { "?" }

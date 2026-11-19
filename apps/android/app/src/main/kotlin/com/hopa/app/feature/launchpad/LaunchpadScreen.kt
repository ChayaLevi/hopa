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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.component.AppIcon
import com.hopa.app.ui.component.HopaPageHeader
import com.hopa.ui.theme.HopaColor
import com.hopa.catalog.routing.RouteTarget

/**
 * The catalogue as a list: apps and sites, the same two sections Harmony shows.
 * An app opens its card, where status, surface and rules live; a site opens in
 * HOPA's browser.
 */
@Composable
fun LaunchpadScreen(
    apps: List<RouteTarget>,
    sites: List<RouteTarget>,
    status: String,
    initialSection: Int = 0,
    onBack: () -> Unit,
    onOpenApp: (RouteTarget) -> Unit,
    onOpenSite: (RouteTarget) -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(initialSection) }
    var query by rememberSaveable { mutableStateOf("") }
    val needle = query.trim().lowercase()
    val shownApps = apps.filter { target -> target.matches(needle) }
    val shownSites = sites.filter { target ->
        target.matches(needle) || target.domains.any { it.lowercase().contains(needle) }
    }

    Scaffold(
        containerColor = HopaColor.Bg,
        topBar = {
            HopaPageHeader(title = "Launchpad", onBack = onBack)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
            LaunchpadSearch(query) { query = it }
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
                            LaunchpadAppRow(target) { onOpenApp(target) }
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
private fun LaunchpadSearch(
    query: String,
    onQuery: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(HopaColor.Surface, RoundedCornerShape(10.dp))
            .border(1.dp, HopaColor.Divider, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(fontSize = 13.sp, color = HopaColor.TextPrimary),
            cursorBrush = SolidColor(HopaColor.Primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text("Search apps and sites", fontSize = 13.sp, color = HopaColor.TextHint)
                }
                inner()
            },
        )
    }
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
) {
    val ready = target.status == "ready" && target.bundle.isNotBlank()
    val word = if (ready) "Installed" else "Not installed"
    val dot = if (ready) HopaColor.Success else HopaColor.Warning
    LaunchpadRow(
        packageName = target.bundle,
        monogram = monogram(target.display.ifBlank { target.name }),
        monogramTint = HopaColor.Primary,
        monogramBg = HopaColor.PrimarySoft,
        onOpen = onOpen,
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
        packageName = "",
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
    packageName: String,
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
        AppIcon(
            packageName = packageName,
            label = monogram,
            size = 36.dp,
            fallbackTint = monogramTint,
            fallbackBg = monogramBg,
        )
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

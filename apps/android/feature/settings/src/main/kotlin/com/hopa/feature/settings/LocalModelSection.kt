package com.hopa.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.inference.local.ModelSpec
import com.hopa.ui.theme.HopaColor

/**
 * The on-device part of the Answers page: the model this phone is offered, the
 * files it already has, and what happens if one goes away.
 *
 * It renders state and reports events; the view model owns everything else, so
 * a screenshot can draw any state without Android.
 */
@Composable
fun LocalModelSection(
    state: LocalModelUiState,
    onImport: () -> Unit,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
) {
    Text(
        "Run the model on this phone",
        fontSize = 16.sp,
        color = HopaColor.TextPrimary,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        "Answers stay on the device. ${state.ramGb} GB of RAM on this phone.",
        style = MaterialTheme.typography.bodySmall,
        color = HopaColor.TextHint,
    )
    val recommended = state.recommended
    if (recommended == null) {
        Text(
            "No model fits the free space right now. Free some space and come back.",
            style = MaterialTheme.typography.bodySmall,
            color = HopaColor.Danger,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    } else {
        val installed = state.installed.any { it.id == recommended.id }
        ModelCard(spec = recommended, installed = installed, onImport = onImport)
    }
    if (state.installed.isNotEmpty()) {
        Text(
            "Installed",
            fontSize = 14.sp,
            color = HopaColor.TextSecondary,
            modifier = Modifier.padding(top = 12.dp),
        )
        state.installed.forEach { model ->
            val spec = state.installedSpecs[model.id]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(spec?.name ?: model.id, fontSize = 13.sp, color = HopaColor.TextPrimary)
                    Text(formatBytes(model.bytes), fontSize = 11.sp, color = HopaColor.TextHint)
                }
                TextButton(onClick = { onDelete(model.id) }) {
                    Text("Delete", color = HopaColor.Danger)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "Models use ${formatBytes(state.totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = HopaColor.TextSecondary,
            )
            TextButton(onClick = onDeleteAll) {
                Text("Delete all", color = HopaColor.Danger)
            }
        }
    }
    state.notice?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = HopaColor.Success)
    }
    state.error?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = HopaColor.Danger)
    }
}

@Composable
private fun ModelCard(spec: ModelSpec, installed: Boolean, onImport: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "${spec.name}  ·  ${formatBytes(spec.bytes)}",
            fontSize = 14.sp,
            color = HopaColor.TextPrimary,
        )
        Text(
            "${spec.license}  ·  ${spec.contextTokens / 1000}k context  ·  " +
                if (installed) "installed" else spec.speedClass,
            style = MaterialTheme.typography.bodySmall,
            color = HopaColor.TextHint,
        )
        spec.strengths.forEach { line ->
            Text("+ $line", style = MaterialTheme.typography.bodySmall, color = HopaColor.TextSecondary)
        }
        spec.weaknesses.forEach { line ->
            Text("− $line", style = MaterialTheme.typography.bodySmall, color = HopaColor.TextSecondary)
        }
        spec.notes.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodySmall, color = HopaColor.TextHint)
        }
        if (!installed) {
            TextButton(onClick = onImport) {
                Text(if (spec.downloadable) "Download" else "Import file")
            }
        }
    }
}

package com.hopa.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.ui.theme.HopaColor

/**
 * The proof sheet: what the turn planned to do, what it actually did, and what it
 * cost. The steps are the same ones written to the run record.
 */
@Composable
fun EvidenceSheet(
    run: RunSummary,
    onSaveSkill: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What happened", fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${run.executed} steps, ${run.verified} confirmed - " +
                        "${run.millis / 1000} s, ${run.tokens} tokens",
                    fontSize = 13.sp,
                    color = HopaColor.TextSecondary,
                )
                Text("Ran on ${where(run.path)}", fontSize = 12.sp, color = HopaColor.TextHint)
                if (run.steps.isEmpty()) {
                    Text("Nothing was tapped this time.", fontSize = 13.sp)
                } else {
                    run.steps.forEach { step ->
                        Text(step, fontSize = 13.sp, color = HopaColor.TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSaveSkill) { Text("Save as a skill") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

private fun where(path: String): String = when (path) {
    "background" -> "the hidden screen"
    "bridge" -> "your computer"
    "accessibility" -> "this screen"
    else -> "no surface"
}

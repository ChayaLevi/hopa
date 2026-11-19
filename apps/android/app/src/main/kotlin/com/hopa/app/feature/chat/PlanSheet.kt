package com.hopa.app.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.ui.theme.HopaColor

/**
 * The plan, before anything is tapped: one approval for the whole chain instead
 * of a question per step, or a silent run.
 */
@Composable
fun PlanSheet(
    steps: List<String>,
    onApprove: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Here is the plan", fontSize = 18.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.forEachIndexed { index, step ->
                    Text(
                        "${index + 1}. $step",
                        fontSize = 13.sp,
                        color = HopaColor.TextPrimary,
                    )
                }
                Text(
                    "Nothing has been tapped yet.",
                    fontSize = 12.sp,
                    color = HopaColor.TextHint,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onApprove) { Text("Do it") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel") }
        },
    )
}

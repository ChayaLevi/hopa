package com.hopa.app.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hopa.app.recipes.Recipe
import com.hopa.app.ui.theme.HopaColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The goals HOPA can repeat without the model, and a way to forget them. */
@Composable
fun SettingsRecipesScreen(
    recipes: List<Recipe>,
    names: Map<String, String> = emptyMap(),
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
) {
    SettingsPage(title = "Saved repetitions", onBack = onBack) {
        Text(
            "A goal that finished cleanly is saved with its steps. Asking for it again " +
                "runs those steps without the model.",
            style = MaterialTheme.typography.bodySmall,
            color = HopaColor.TextHint,
        )
        if (recipes.isEmpty()) {
            Text("Nothing saved yet.", fontSize = 13.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            recipes.sortedByDescending { it.at }.forEach { recipe ->
                SettingsPanel {
                    Text(recipe.goal, fontSize = 14.sp, color = HopaColor.TextPrimary)
                    Text(
                        listOfNotNull(
                            names[recipe.app.lowercase()]?.takeIf { it.isNotBlank() }
                                ?.let { "in $it" },
                            "${recipe.steps.size} steps",
                            clock(recipe.at),
                        ).joinToString(" - "),
                        fontSize = 12.sp,
                        color = HopaColor.TextSecondary,
                    )
                    TextButton(onClick = { onDelete(recipe.goal) }) {
                        Text("Forget this one", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun clock(at: Long): String =
    SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(at))

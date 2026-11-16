package com.hopa.app.skills

import com.hopa.core.contracts.Action
import java.io.File

/**
 * Recipes on disk: goal and the actions that worked, one line each. Replaying a
 * recipe is the cheap path - no model, no tokens, the same taps.
 */
class RecipeStore(file: File) {
    private val file = file
    private val separator = "\u001f"

    fun save(goal: String, steps: List<Action>) {
        if (goal.isBlank() || steps.isEmpty()) {
            return
        }
        val existing = all().filterNot { normalise(it.goal) == normalise(goal) }
        val lines = (existing.takeLast(MAX) + Recipe(goal, steps, System.currentTimeMillis()))
            .map(::encode)
        runCatching { file.writeText(lines.joinToString("\n") + "\n") }
    }

    fun find(goal: String): Recipe? =
        all().firstOrNull { normalise(it.goal) == normalise(goal) }

    fun all(): List<Recipe> {
        if (!file.exists()) {
            return emptyList()
        }
        return runCatching {
            file.readLines().mapNotNull(::decode)
        }.getOrDefault(emptyList())
    }

    fun delete(goal: String) {
        val remaining = all().filterNot { normalise(it.goal) == normalise(goal) }
        runCatching {
            file.writeText(remaining.joinToString("\n") + if (remaining.isEmpty()) "" else "\n")
        }
    }

    fun clear() {
        runCatching { file.delete() }
    }

    private fun normalise(value: String): String =
        value.lowercase().replace(Regex("\\s+"), " ").trim()

    private fun encode(recipe: Recipe): String =
        recipe.at.toString() + separator +
            recipe.goal.replace('\n', ' ') + separator +
            recipe.steps.joinToString(ACTION_SEP) { action ->
                listOf(
                    action.kind,
                    action.app.orEmpty(),
                    action.ref.orEmpty(),
                    action.text.orEmpty(),
                    action.url.orEmpty(),
                    action.x?.toString().orEmpty(),
                    action.y?.toString().orEmpty(),
                    action.toX?.toString().orEmpty(),
                    action.toY?.toString().orEmpty(),
                    action.timeoutMs.toString(),
                ).joinToString(separator)
            }

    private fun decode(line: String): Recipe? {
        val parts = line.split(separator)
        if (parts.size < 3) {
            return null
        }
        val at = parts[0].toLongOrNull() ?: return null
        val goal = parts[1]
        val steps = parts.drop(2).joinToString(separator)
            .split(ACTION_SEP)
            .mapNotNull { raw ->
                val f = raw.split(separator)
                if (f.size < 10) {
                    return@mapNotNull null
                }
                Action(
                    kind = f[0],
                    app = f[1].ifBlank { null },
                    ref = f[2].ifBlank { null },
                    text = f[3].ifBlank { null },
                    url = f[4].ifBlank { null },
                    x = f[5].toIntOrNull(),
                    y = f[6].toIntOrNull(),
                    toX = f[7].toIntOrNull(),
                    toY = f[8].toIntOrNull(),
                    timeoutMs = f[9].toIntOrNull() ?: 3000,
                )
            }
        return if (steps.isEmpty()) null else Recipe(goal, steps, at)
    }

    private companion object {
        const val MAX = 30
        const val ACTION_SEP = "\u001e"
    }
}

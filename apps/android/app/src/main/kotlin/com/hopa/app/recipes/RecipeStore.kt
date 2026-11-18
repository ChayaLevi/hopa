package com.hopa.app.recipes

import com.hopa.contracts.Action
import java.io.File

/**
 * Recipes on disk: goal and the actions that worked, one line each. Replaying a
 * recipe is the cheap path - no model, no tokens, the same taps.
 */
class RecipeStore(file: File) {
    private val file = file
    private val separator = "\u001f"

    fun save(
        goal: String,
        steps: List<Action>,
        anchors: List<String> = emptyList(),
        screenHash: String = "",
        endHash: String = "",
    ) {
        if (goal.isBlank() || steps.isEmpty()) {
            return
        }
        val existing = all().filterNot { normalise(it.goal) == normalise(goal) }
        val lines = (
            existing.takeLast(MAX) +
                Recipe(goal, steps, System.currentTimeMillis(), anchors, screenHash, endHash)
            )
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
        VERSION + separator +
            recipe.at.toString() + separator +
            recipe.goal.replace('\n', ' ') + separator +
            recipe.screenHash + separator +
            recipe.endHash + separator +
            recipe.steps.mapIndexed { index, action ->
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
                    recipe.anchorAt(index),
                ).joinToString(separator)
            }.joinToString(ACTION_SEP)

    private fun decode(line: String): Recipe? {
        var parts = line.split(separator)
        // A version marker makes the hash fields unambiguous: without it a line
        // with an empty hash looks like the oldest format.
        val versioned = parts.firstOrNull() == VERSION
        if (versioned) {
            parts = parts.drop(1)
        }
        if (parts.size < 3) {
            return null
        }
        val at = parts[0].toLongOrNull() ?: return null
        val goal = parts[1]
        // Two older shapes: hashes but no anchors, and steps straight after the goal.
        val hasHashes = versioned || (parts.size >= 5 && parts[2].startsWith(HASH_PREFIX))
        val screenHash = if (hasHashes) parts[2] else ""
        val endHash = if (hasHashes) parts.getOrNull(3).orEmpty() else ""
        val steps = parts.drop(if (hasHashes) 4 else 2).joinToString(separator)
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
                ) to f.getOrNull(10).orEmpty()
            }
        return if (steps.isEmpty()) {
            null
        } else {
            Recipe(
                goal = goal,
                steps = steps.map { it.first },
                at = at,
                anchors = steps.map { it.second },
                screenHash = screenHash,
                endHash = endHash,
            )
        }
    }

    private companion object {
        const val MAX = 30
        const val ACTION_SEP = "\u001e"
        const val HASH_PREFIX = "sha256:"
        const val VERSION = "v2"
    }
}

package com.hopa.app.recipes

import com.hopa.app.di.AppContainer
import com.hopa.contracts.Observation
import com.hopa.contracts.ports.DevicePort
import kotlinx.coroutines.delay

/**
 * Replays a recipe on the current device. It is deliberately strict: it refuses
 * to start when the screen is not the screen the recipe was recorded on, and it
 * stops at the first step that is refused or fails, so a changed screen never
 * becomes a series of wrong taps.
 *
 * Tree positions are not stable between runs: `ref = e21` in a recipe points at
 * whatever is 21st in the *current* tree. The start hash is what makes a
 * ref-based replay honest; without it the recipe is not replayed at all.
 */
class Replayer(private val container: AppContainer) {
    suspend fun replay(recipe: Recipe, device: DevicePort): ReplayOutcome {
        if (recipe.app.isNotBlank()) {
            val installed = runCatching { container.packages.installed() }.getOrDefault(emptyList())
            if (installed.none { it.packageName.equals(recipe.app, ignoreCase = true) }) {
                // The app the recipe was recorded in is gone; let the model plan again.
                return ReplayOutcome(0, complete = false, endMatched = false)
            }
        }
        var observation = runCatching { device.observe() }.getOrNull()
            ?: return ReplayOutcome(0, complete = false, endMatched = false)
        if (!startsWhereRecorded(recipe, observation)) {
            return ReplayOutcome(0, complete = false, endMatched = false)
        }
        val executor = container.executorFor(device)
        var done = 0
        for (step in recipe.steps) {
            val result = executor.execute(
                runId = "replay-${System.currentTimeMillis()}",
                step = done + 1,
                traceId = "replay",
                action = step,
                before = observation,
            )
            if (!result.record.executed) {
                return ReplayOutcome(done, complete = false, endMatched = false)
            }
            observation = result.observation
            done += 1
            delay(1_200)
        }
        val end = runCatching { device.observe() }.getOrNull()
        return ReplayOutcome(
            done = done,
            complete = done == recipe.steps.size,
            endMatched = end != null && endsWhereRecorded(recipe, end),
        )
    }

    private fun startsWhereRecorded(recipe: Recipe, observation: Observation): Boolean {
        // Positions are not stable between runs: `e21` in a recipe points at
        // whatever is 21st in the current tree. Until a step records what it
        // pointed at (text, description, id), a ref step is a guess, so the recipe
        // waits for the model instead of tapping.
        if (recipe.needsStableScreen) return false
        if (recipe.screenHash.isBlank()) return true
        return observation.hash == recipe.screenHash
    }

    private fun endsWhereRecorded(recipe: Recipe, observation: Observation): Boolean =
        recipe.endHash.isBlank() || observation.hash == recipe.endHash
}

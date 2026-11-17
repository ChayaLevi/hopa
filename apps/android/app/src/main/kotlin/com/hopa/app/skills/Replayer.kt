package com.hopa.app.skills

import com.hopa.app.di.AppContainer
import com.hopa.engine.ports.DevicePort
import kotlinx.coroutines.delay

/**
 * Replays a recipe on the current device. It is deliberately strict: the first
 * step that is refused or fails ends the replay and the caller falls back to the
 * model, so a changed screen never becomes a series of wrong taps.
 */
class Replayer(private val container: AppContainer) {
    suspend fun replay(recipe: Recipe, device: DevicePort): Int {
        if (recipe.app.isNotBlank()) {
            val installed = runCatching { container.packages.installed() }.getOrDefault(emptyList())
            if (installed.none { it.packageName.equals(recipe.app, ignoreCase = true) }) {
                // The app the recipe was recorded in is gone; let the model plan again.
                return 0
            }
        }
        val executor = container.executorFor(device)
        var observation = runCatching { device.observe() }.getOrNull() ?: return 0
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
                return done
            }
            observation = result.observation
            done += 1
            delay(1_200)
        }
        return done
    }
}

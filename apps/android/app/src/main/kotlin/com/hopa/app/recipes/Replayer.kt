package com.hopa.app.recipes

import com.hopa.app.di.AppContainer
import com.hopa.contracts.ports.DevicePort
import com.hopa.engine.observation.NodeAnchor
import kotlinx.coroutines.delay

/**
 * Replays a recipe on the current device.
 *
 * A step that carries a `ref` is re-resolved through the anchor recorded with
 * it: the text, description or id of the control, not its position in the tree.
 * If the anchor is missing, or is not on the screen any more, the replay stops
 * before that tap and the caller falls back to the model. A wrong tap is worse
 * than a slower answer.
 */
class Replayer(private val container: AppContainer) {
    suspend fun replay(recipe: Recipe, device: DevicePort): ReplayOutcome {
        if (!recipe.replayable) {
            return ReplayOutcome(0, complete = false)
        }
        if (recipe.app.isNotBlank()) {
            val installed = runCatching { container.packages.installed() }.getOrDefault(emptyList())
            if (installed.none { it.packageName.equals(recipe.app, ignoreCase = true) }) {
                // The app the recipe was recorded in is gone; let the model plan again.
                return ReplayOutcome(0, complete = false)
            }
        }
        var observation = runCatching { device.observe() }.getOrNull()
            ?: return ReplayOutcome(0, complete = false)
        val executor = container.executorFor(device)
        var done = 0
        for ((index, step) in recipe.steps.withIndex()) {
            val anchored = if (step.ref.isNullOrBlank()) {
                step
            } else {
                val ref = NodeAnchor.refOf(observation.tree, recipe.anchorAt(index))
                    ?: return ReplayOutcome(done, complete = false)
                step.copy(ref = ref)
            }
            val result = executor.execute(
                runId = "replay-${System.currentTimeMillis()}",
                step = done + 1,
                traceId = "replay",
                action = anchored,
                before = observation,
            )
            if (!result.record.executed) {
                return ReplayOutcome(done, complete = false)
            }
            observation = result.observation
            done += 1
            delay(1_200)
        }
        return ReplayOutcome(done = done, complete = done == recipe.steps.size, screen = observation)
    }
}

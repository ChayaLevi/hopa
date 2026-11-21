package com.hopa.decision

/**
 * Asks the selected layer, records every vote, and says which one applies.
 *
 * One layer failing is a vote with an error, not an exception into the loop:
 * the step then falls back to the model path unless the mode is Off. Shadow
 * votes are recorded but never applied.
 */
class DecisionCoordinator(
    private val registry: DecisionRegistry,
    private val selector: DecisionSelector = DecisionSelector(registry),
    private val nanoTime: () -> Long = System::nanoTime,
) {
    suspend fun evaluate(
        context: DecisionContext,
        mode: DecisionMode,
        shadow: Boolean = false,
        sink: DecisionVoteSink? = null,
    ): DecisionRun {
        if (context.cancelled) {
            return DecisionRun.NONE
        }
        if (mode is DecisionMode.Off && !shadow) {
            return DecisionRun.NONE
        }
        val selection = selector.select(mode, context.actionClass)
        val chosen = selection.layer
        val voters = selector.voters(mode, context.actionClass, shadow)
        if (voters.isEmpty()) {
            return DecisionRun(applied = null, votes = emptyList(), notice = selection.notice)
        }
        val votes = voters.map { layer -> measure(layer, context, applied = layer === chosen) }
        val applied = votes.firstOrNull { it.applied && it.error == null }
        val notice = when {
            chosen != null && applied == null ->
                "The layer \"${chosen.metadata.id}\" failed; the model decides this step."
            else -> selection.notice
        }
        val run = DecisionRun(applied = applied, votes = votes, notice = notice)
        sink?.record(context, run)
        return run
    }

    private suspend fun measure(layer: DecisionLayer, context: DecisionContext, applied: Boolean): DecisionVote {
        val id = layer.metadata.id
        val version = layer.metadata.version
        val started = nanoTime()
        return try {
            val decision = layer.decide(context)
            DecisionVote(
                layerId = id,
                layerVersion = version,
                verb = decision.verb,
                reason = decision.reason,
                confidence = decision.confidence,
                trace = decision.trace,
                latencyMicros = elapsedMicros(started),
                applied = applied,
                chosen = decision.chosen,
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            DecisionVote(
                layerId = id,
                layerVersion = version,
                verb = null,
                reason = "layer_error",
                confidence = 0.0,
                trace = error.message.orEmpty().ifBlank { error::class.simpleName.orEmpty() },
                latencyMicros = elapsedMicros(started),
                applied = applied,
                error = error::class.simpleName.orEmpty().ifBlank { "failed" },
            )
        }
    }

    private fun elapsedMicros(started: Long): Long = ((nanoTime() - started) / 1_000).coerceAtLeast(0)
}

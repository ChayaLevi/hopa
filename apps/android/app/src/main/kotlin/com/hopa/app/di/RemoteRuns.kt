package com.hopa.app.di

import com.hopa.app.gateway.RemoteRunRequest
import com.hopa.execution.TurnResult

/**
 * A delivered task takes the same turn as chat: plan-free (the sender already
 * asked), recipe first, the same loop and the same recipe-saving rule. Only the
 * surface and the socket are the delivery's own.
 */
object RemoteRuns {
    suspend fun execute(container: AppContainer, request: RemoteRunRequest): TurnResult {
        container.useGate(request.settings.trustedApps.toList())
        val outcome = container.runs.run(
            container.runs.request(
                goal = request.goal,
                device = request.device,
                settings = request.settings,
                runId = request.runId,
                traceId = request.traceId,
            ),
            request.isCancelled,
        )
        return TurnResult(
            answer = outcome.answer,
            records = outcome.records,
            narration = outcome.narration,
            options = outcome.options,
            error = outcome.error,
            usage = outcome.usage,
        )
    }
}

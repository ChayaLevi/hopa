package com.hopa.data.store

import com.hopa.contracts.ports.Clock
import com.hopa.data.database.dao.DecisionVoteDao
import com.hopa.data.database.entity.DecisionVoteEntity
import com.hopa.decision.DecisionContext
import com.hopa.decision.DecisionMode
import com.hopa.decision.DecisionRun
import com.hopa.decision.DecisionVoteSink
import kotlinx.coroutines.CancellationException

/**
 * Writes every vote of a step, including shadow votes, into the run log table.
 *
 * A logging failure must never stop a turn, so this sink swallows everything
 * except cancellation and keeps the step going. The mode is a supplier: a
 * setting changed between steps is recorded with the vote it produced.
 */
class DbDecisionVoteSink(
    private val dao: DecisionVoteDao,
    private val clock: Clock,
    private val mode: () -> DecisionMode,
) : DecisionVoteSink {
    override suspend fun record(context: DecisionContext, run: DecisionRun) {
        if (run.votes.isEmpty()) {
            return
        }
        val at = clock.nowMillis()
        val storedMode = mode().stored
        val actionKind = context.action?.kind.orEmpty()
        val actionClass = context.actionClass.name
        val rows = run.votes.map { vote ->
            DecisionVoteEntity(
                runId = context.runId,
                step = context.step,
                layerId = vote.layerId,
                layerVersion = vote.layerVersion,
                actionKind = actionKind,
                actionClass = actionClass,
                mode = storedMode,
                verb = vote.verb?.name?.lowercase(),
                reason = vote.reason,
                confidence = vote.confidence,
                trace = vote.trace,
                latencyMicros = vote.latencyMicros,
                applied = vote.applied && vote.error == null,
                error = vote.error,
                notice = run.notice,
                at = at,
            )
        }
        try {
            dao.insertAndPrune(rows, KEEP)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // The vote is data about a step, not the step itself.
        }
    }

    private companion object {
        /** Enough for weeks of real turns without turning the log into storage. */
        const val KEEP = 5_000
    }
}

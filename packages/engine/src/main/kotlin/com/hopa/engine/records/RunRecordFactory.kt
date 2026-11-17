package com.hopa.engine.records

import com.hopa.engine.contracts.Action
import com.hopa.engine.contracts.Observation
import com.hopa.engine.observation.ObservationHasher
import com.hopa.engine.ports.Clock
import java.time.Instant

class RunRecordFactory(
    private val clock: Clock,
) {
    fun draft(
        runId: String,
        step: Int,
        traceId: String,
        action: Action,
        classification: String,
        approved: Boolean?,
        observation: Observation,
        beforeHash: String?,
        afterHash: String?,
        verified: Boolean,
        executed: Boolean,
        error: String? = null,
    ): RunRecordDraft = RunRecordDraft(
        runId = runId,
        step = step,
        timestamp = Instant.ofEpochMilli(clock.nowMillis()).toString(),
        traceId = traceId,
        action = action,
        classification = classification,
        approved = approved,
        observationHash = ObservationHasher.hash(observation),
        beforeHash = beforeHash,
        afterHash = afterHash,
        verified = verified,
        executed = executed,
        error = error,
    )
}

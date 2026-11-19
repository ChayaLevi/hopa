package com.hopa.runlog.store

import com.hopa.contracts.wire.RunRecord
import com.hopa.runlog.record.RunRecordDraft

interface RunRecorder {
    suspend fun append(draft: RunRecordDraft): RunRecord

    suspend fun records(runId: String): List<RunRecord>

    suspend fun all(): List<RunRecord>

    suspend fun verify(runId: String): Boolean

    suspend fun clear()
}

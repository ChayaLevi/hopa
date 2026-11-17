package com.hopa.data.records

import com.hopa.engine.contracts.RunRecord
import com.hopa.engine.records.RunChain
import com.hopa.engine.records.RunRecordDraft
import com.hopa.engine.records.RunRecorder
import com.hopa.data.database.dao.RunRecordDao
import com.hopa.data.database.mapper.RunRecordMapper

class RoomRunRecorder(
    private val dao: RunRecordDao,
) : RunRecorder {
    override suspend fun append(draft: RunRecordDraft): RunRecord {
        val previous = dao.last(draft.runId)?.hash ?: RunChain.SEED
        val record = RunChain.seal(draft, previous)
        dao.insertAndPrune(RunRecordMapper.entity(record), keep = 100)
        return record
    }

    override suspend fun records(runId: String): List<RunRecord> =
        dao.forRun(runId).map(RunRecordMapper::record)

    override suspend fun all(): List<RunRecord> =
        dao.all().map(RunRecordMapper::record)

    override suspend fun verify(runId: String): Boolean =
        RunChain.verify(records(runId))

    override suspend fun clear() {
        dao.clearAll()
    }
}

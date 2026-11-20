package com.hopa.feature.runs

import com.hopa.contracts.wire.RunRecord

/** What the runs page shows: the audit trail, newest first. */
data class RunsUiState(
    val records: List<RunRecord> = emptyList(),
)

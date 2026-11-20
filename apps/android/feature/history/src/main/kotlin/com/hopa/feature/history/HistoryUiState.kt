package com.hopa.feature.history

import com.hopa.data.database.entity.RunHistoryEntity

/** Finished turns, filtered by the surface they ran on. */
data class HistoryUiState(
    val rows: List<RunHistoryEntity> = emptyList(),
    val filter: String = "",
)

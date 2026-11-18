package com.hopa.app.feature.history

import com.hopa.contracts.RunRecord

data class ActivityUiState(
    val records: List<RunRecord> = emptyList(),
    val weekActions: Int = 0,
    val weekDone: Int = 0,
    val weekApps: Int = 0,
    val lastGoal: String = "",
    val lastSteps: List<String> = emptyList(),
    val busy: Boolean = false,
    val ready: Boolean = false,
)

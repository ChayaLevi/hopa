package com.hopa.feature.power

import com.hopa.contracts.wire.RunRecord

/** What the Power home shows: the most recent runs. */
data class PowerHomeUiState(
    val recent: List<RunRecord> = emptyList(),
)

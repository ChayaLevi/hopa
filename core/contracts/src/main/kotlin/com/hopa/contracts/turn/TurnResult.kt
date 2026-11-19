package com.hopa.contracts.turn

import com.hopa.contracts.model.ModelUsage
import com.hopa.contracts.wire.RunRecord
import com.hopa.contracts.error.HopaError

data class TurnResult(
    val answer: String,
    val records: List<RunRecord> = emptyList(),
    val narration: List<String> = emptyList(),
    val options: List<String> = emptyList(),
    val error: HopaError? = null,
    val usage: ModelUsage = ModelUsage(),
)

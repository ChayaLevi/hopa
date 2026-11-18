package com.hopa.engine.loop

import com.hopa.contracts.HopaError
import com.hopa.contracts.RunRecord
import com.hopa.contracts.model.ModelUsage

data class TurnResult(
    val answer: String,
    val records: List<RunRecord> = emptyList(),
    val narration: List<String> = emptyList(),
    val options: List<String> = emptyList(),
    val error: HopaError? = null,
    val usage: ModelUsage = ModelUsage(),
)

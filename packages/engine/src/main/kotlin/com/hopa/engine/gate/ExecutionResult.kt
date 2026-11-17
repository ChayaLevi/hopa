package com.hopa.engine.gate

import com.hopa.engine.contracts.Observation
import com.hopa.engine.contracts.RunRecord

data class ExecutionResult(
    val record: RunRecord,
    val observation: Observation,
    val text: String = "",
)

package com.hopa.contracts

import com.hopa.contracts.wire.Observation
import com.hopa.contracts.wire.RunRecord

data class ExecutionResult(
    val record: RunRecord,
    val observation: Observation,
    val text: String = "",
)

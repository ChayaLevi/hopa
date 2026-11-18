package com.hopa.contracts

import com.hopa.contracts.Observation
import com.hopa.contracts.RunRecord

data class ExecutionResult(
    val record: RunRecord,
    val observation: Observation,
    val text: String = "",
)

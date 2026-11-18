package com.hopa.contracts

data class ExecutionResult(
    val record: RunRecord,
    val observation: Observation,
    val text: String = "",
)

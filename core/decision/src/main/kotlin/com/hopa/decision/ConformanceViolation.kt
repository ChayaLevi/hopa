package com.hopa.decision

/** One rule a layer broke in one conformance case. */
data class ConformanceViolation(
    val caseId: String,
    val rule: String,
    val detail: String,
)

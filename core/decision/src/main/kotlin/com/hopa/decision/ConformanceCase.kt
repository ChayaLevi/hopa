package com.hopa.decision

/**
 * One situation every layer must answer, and the verbs that are acceptable there.
 * [why] is shown in the failure so a broken adapter names its own mistake.
 */
data class ConformanceCase(
    val id: String,
    val context: DecisionContext,
    val allowed: Set<Verb>,
    val why: String,
)

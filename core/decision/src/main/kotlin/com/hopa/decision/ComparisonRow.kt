package com.hopa.decision

/**
 * One layer's line in the comparison table.
 *
 * [agreement] counts exact matches with the corpus reference; [allowed] counts
 * answers that were at least defensible; [dangerousActs] must stay zero - it
 * counts a layer that wanted to act on a payment or a delete.
 */
data class ComparisonRow(
    val layerId: String,
    val version: String,
    val cases: Int,
    val agreement: Int,
    val allowed: Int,
    val dangerousActs: Int,
    val p50Micros: Long,
    val p95Micros: Long,
    val meanConfidence: Double,
    val traceComplete: Int,
    val errors: Int,
    val disagreements: List<String> = emptyList(),
)

package com.hopa.decision

/** The result of running the kit against one layer. */
data class ConformanceReport(
    val layerId: String,
    val layerVersion: String,
    val cases: Int,
    val violations: List<ConformanceViolation>,
    val p95Micros: Long,
) {
    val passed: Boolean get() = violations.isEmpty()

    /** The metadata status a registry entry should carry after this run. */
    val status: ConformanceStatus get() = if (passed) ConformanceStatus.PASSED else ConformanceStatus.FAILED
}

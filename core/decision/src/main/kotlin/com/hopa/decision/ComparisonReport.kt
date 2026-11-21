package com.hopa.decision

/** The comparison table for every layer that was asked, plus the model baseline. */
data class ComparisonReport(
    val rows: List<ComparisonRow>,
    val cases: List<DecisionCase>,
) {
    /** Markdown, because this table is pasted into a PR or a plan document. */
    fun toMarkdown(): String {
        val header = "| Layer | Version | Agreement | Allowed | Dangerous ACT | p50 µs | p95 µs | " +
            "Mean confidence | Trace | Errors |"
        val rule = "|---|---|---|---|---|---|---|---|---|---|"
        val lines = mutableListOf(header, rule)
        for (row in rows) {
            lines += "| ${row.layerId} | ${row.version} | ${ratio(row.agreement, row.cases)} | " +
                "${ratio(row.allowed, row.cases)} | ${row.dangerousActs} | ${row.p50Micros} | " +
                "${row.p95Micros} | ${format(row.meanConfidence)} | " +
                "${ratio(row.traceComplete, row.cases)} | ${row.errors} |"
        }
        val disagreements = rows.filter { it.disagreements.isNotEmpty() }
        if (disagreements.isNotEmpty()) {
            lines += ""
            lines += "Disagreements:"
            for (row in disagreements) {
                lines += "- ${row.layerId}: ${row.disagreements.joinToString(", ")}"
            }
        }
        return lines.joinToString("\n")
    }

    private fun ratio(part: Int, total: Int): String =
        if (total == 0) "n/a" else "${part * 100 / total}% ($part/$total)"

    private fun format(value: Double): String = String.format(java.util.Locale.ROOT, "%.2f", value)
}

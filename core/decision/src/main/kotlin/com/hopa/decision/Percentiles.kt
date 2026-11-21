package com.hopa.decision

/** Nearest-rank percentiles for small samples; no statistics dependency. */
object Percentiles {
    fun of(values: List<Long>, percent: Int): Long {
        if (values.isEmpty()) {
            return 0
        }
        require(percent in 0..100) { "percent must be in 0..100: $percent" }
        val sorted = values.sorted()
        val index = ((sorted.size * percent + 99) / 100 - 1).coerceIn(0, sorted.size - 1)
        return sorted[index]
    }
}

package com.hopa.decision

/** How many times a layer may still interrupt the person in this turn. */
data class AttentionBudget(val left: Int = 3) {
    init {
        require(left >= 0) { "attention budget cannot be negative: $left" }
    }

    fun spend(): AttentionBudget = AttentionBudget((left - 1).coerceAtLeast(0))

    fun isSpent(): Boolean = left == 0
}

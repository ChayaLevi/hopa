package com.hopa.family.judgment

/** A message's tier, why, and the words the rule matched (absent for [JudgmentReason.NO_DATE]). */
data class Judged(
    val tier: MessageTier,
    val reason: JudgmentReason,
    val quote: String? = null,
)

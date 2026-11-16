package com.hopa.app.feature.chat

/** What a finished turn did, in the words a person can check. */
data class RunSummary(
    val goal: String,
    val path: String,
    val steps: List<String>,
    val executed: Int,
    val verified: Int,
    val tokens: Int,
    val millis: Long,
) {
    val allVerified: Boolean get() = executed > 0 && verified == executed
}

package com.hopa.family.intents

/** What a model job gave: a checked value, or why not (with the problems of the last reply). */
sealed interface JobResult<out T> {
    val attempts: Int

    data class Ok<T>(val value: T, override val attempts: Int) : JobResult<T>

    /** [code]: a [ModelErrorCode] wire name, or `invalid` when the model's replies did not pass. */
    data class Failed(
        val code: String,
        val message: String,
        val problems: List<String>? = null,
        override val attempts: Int,
    ) : JobResult<Nothing>
}

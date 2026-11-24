package com.hopa.contracts.a2ui

/** Why a message was refused; [path] is a JSON Pointer into that message. */
data class A2uiProblem(
    val code: String,
    val path: String,
    val message: String,
)

sealed interface A2uiResult<out T> {
    data class Ok<T>(val value: T) : A2uiResult<T>

    data class Refused(val problem: A2uiProblem) : A2uiResult<Nothing>
}

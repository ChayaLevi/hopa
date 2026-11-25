package com.hopa.backend.board

import com.hopa.backend.protocol.GatewayError

/** A board call's answer: a value, or a stable error code the client acts on. */
sealed interface BoardResult<out T> {
    data class Ok<T>(val value: T) : BoardResult<T>

    data class Failed(val error: GatewayError) : BoardResult<Nothing>
}

package com.hopa.backend.board.client

/** What the server said to an appended op. */
sealed interface AppendResult {
    data class Acked(val scope: String, val seq: Long, val duplicate: Boolean) : AppendResult

    /** `E_SCOPE`, `E_CONFLICT`, `E_SCHEMA`, `E_RATE`, ...: screens read [code], never the message. */
    data class Refused(val code: String, val retryable: Boolean) : AppendResult
}

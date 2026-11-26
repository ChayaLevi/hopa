package com.hopa.backend.link

import com.hopa.backend.wire.BackendEnvelope
import com.hopa.backend.wire.BackendReplies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * The app's only view of the gateway socket. The Android layer holds one of
 * these and never sees OkHttp, so the connection is testable on the JVM.
 */
interface BackendLink {
    val state: StateFlow<BackendState>
    var onTask: (suspend (BackendEnvelope) -> Unit)?

    /**
     * Plaintext `diag.*` deliveries. Android leaves this null: a diagnostic is
     * not a goal-bearing task and must not become a `task.create`. The sidecar
     * gateway wires it to its diagnostic handler.
     */
    var onDiagnostic: (suspend (BackendEnvelope) -> Unit)?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}

    /**
     * Family board frames: `board.op.ack`, `board.changed`, and `error` (the gateway answers a
     * `board.op` with one or the other, in send order). Null where the board is not wired.
     */
    var onBoard: (suspend (BackendEnvelope) -> Unit)?
        get() = null
        set(@Suppress("UNUSED_PARAMETER") value) {}

    /** A `task.needs_approval` delivery: show the question, not run a goal. */
    var onApproval: (suspend (BackendEnvelope) -> Unit)?
    fun start(scope: CoroutineScope)
    suspend fun stop()

    /** Sends any frame, e.g. one built by [BackendReplies]. */
    suspend fun send(frame: BackendEnvelope)

    /** Tells the sender of a delivered task that it arrived. */
    suspend fun ack(task: BackendEnvelope)

    /** Application-level liveness check: sends `ping`, waits for `pong`. */
    suspend fun ping()
}

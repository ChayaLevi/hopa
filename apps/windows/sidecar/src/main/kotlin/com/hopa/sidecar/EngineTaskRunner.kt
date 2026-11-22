package com.hopa.sidecar

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Adapts the engine's fire-and-forget turns to the gateway's
 * `suspend (goal) -> answer` (R31).
 *
 * [run] starts one turn under a fresh id and suspends until the engine calls
 * [complete] from its `onTurnEnd` hook. Cancelling the caller cancels the
 * engine's current turn. The lambdas keep the adapter testable without a
 * model; production supplies `engine.startTurn` and `engine.onTurnEnd`.
 *
 * Turns are serialized with a lock because the engine runs one at a time;
 * the gateway may deliver a second `task.create` while the first is still
 * running.
 */
class EngineTaskRunner(
    private val startTurn: (id: String, goal: String) -> Unit,
    private val cancelTurn: () -> Unit = {},
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val turns = Mutex()

    suspend fun run(goal: String): String = turns.withLock { awaitTurn(goal) }

    private suspend fun awaitTurn(goal: String): String {
        val id = newId()
        val answer = CompletableDeferred<String>()
        pending[id] = answer
        try {
            startTurn(id, goal)
            return suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation {
                    pending.remove(id)
                    cancelTurn()
                }
                answer.invokeOnCompletion { cause ->
                    if (cause == null) {
                        continuation.resume(answer.getCompleted())
                    } else {
                        continuation.resumeWithException(cause)
                    }
                }
            }
        } catch (failed: Throwable) {
            pending.remove(id)
            throw failed
        }
    }

    /** The engine's `onTurnEnd` hook; an unknown id is ignored. */
    fun complete(id: String, answer: String) {
        pending.remove(id)?.complete(answer)
    }
}

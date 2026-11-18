package com.hopa.capabilities.display.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One owner for the helper's life: start it when nothing answers, say whether it
 * is alive, and stop it when the person asks.
 *
 * Before this the start was fire-and-forget: `setsid app_process ... &` returned
 * "started" whatever happened next, and a helper left behind by an old install
 * kept running with `ppid=1` while the app believed the display was stopped.
 *
 * Every effect is injected - [ping], [start], [stop], [pause] - so the state
 * machine is unit tested without a phone, a socket or adb.
 */
class HelperSupervisor(
    private val ping: () -> Boolean,
    private val start: suspend () -> Boolean,
    private val stop: () -> Unit,
    private val pause: suspend (Long) -> Unit,
    private val settleMs: Long = SETTLE_MS,
    private val attempts: Int = ATTEMPTS,
) {
    private val lock = Mutex()
    private val _status = MutableStateFlow<HelperStatus>(HelperStatus.Stopped)
    val status: StateFlow<HelperStatus> = _status.asStateFlow()

    /**
     * Make sure a helper answers, starting one if needed. Called before a turn
     * opens the background display, so every turn begins with an answer or a
     * reason, never with a socket that "looks" connected.
     */
    suspend fun ensure(): HelperStatus = lock.withLock {
        if (answers()) {
            return@withLock HelperStatus.Running
        }
        _status.value = HelperStatus.Starting
        val started = runCatching { start() }.getOrDefault(false)
        if (!started) {
            return@withLock fail("The hidden screen did not start. Open Act and set it up.")
        }
        // adbd answers "started" before the socket is bound; give it a moment.
        repeat(attempts) {
            if (answers()) {
                return@withLock HelperStatus.Running
            }
            pause(settleMs)
        }
        fail("The hidden screen started but did not answer.")
    }

    /**
     * A check without a start: used when a screen opens, so a helper that died
     * while the app was away is named instead of being called running.
     */
    suspend fun health(): HelperStatus = lock.withLock {
        if (answers()) HelperStatus.Running else fail("The hidden screen stopped answering.")
    }

    /** The display was released: drop the socket and say so. */
    fun release() {
        runCatching { stop() }
        _status.value = HelperStatus.Stopped
    }

    private fun answers(): Boolean {
        val alive = runCatching { ping() }.getOrDefault(false)
        if (alive) {
            _status.value = HelperStatus.Running
        }
        return alive
    }

    private fun fail(reason: String): HelperStatus {
        _status.value = HelperStatus.Failed(reason)
        return _status.value
    }

    private companion object {
        /** Longer than adbd needs to fork app_process and bind the socket. */
        const val SETTLE_MS = 500L
        const val ATTEMPTS = 10
    }
}

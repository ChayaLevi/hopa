package com.hopa.backend.gateway

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The kick teardown must end the read loop even when the peer never answers the
 * Close frame. A real client here would auto-answer; the helper is the unit
 * under test so `sendClose` can model Ktor returning after the flush while the
 * incoming channel stays open, or a fully stalled handshake.
 */
class KickTeardownTest {
    @Test
    fun `a close frame that returned does not leave incoming running`() = runBlocking {
        val closed = CompletableDeferred<Unit>()
        val closeStarted = AtomicBoolean(false)
        val incomingCancelled = AtomicBoolean(false)
        closed.complete(Unit)

        // Ktor's close() returns once the frame is flushed; the peer's silence
        // is what would otherwise keep the read loop alive forever.
        closeKickedSession(
            closed = closed,
            graceMs = 100,
            sendClose = { closeStarted.set(true) },
            cancelIncoming = { incomingCancelled.set(true) },
        )

        assertTrue(closeStarted.get(), "the close frame must be attempted")
        assertTrue(
            incomingCancelled.get(),
            "incoming must be cancelled even when close() returns promptly",
        )
    }

    @Test
    fun `a close that never returns still tears down inside the bound`() = runBlocking {
        val closed = CompletableDeferred<Unit>()
        val closeStarted = AtomicBoolean(false)
        val incomingCancelled = AtomicBoolean(false)
        closed.complete(Unit)

        val startedAt = System.nanoTime()
        closeKickedSession(
            closed = closed,
            graceMs = 100,
            sendClose = {
                closeStarted.set(true)
                awaitCancellation()
            },
            cancelIncoming = { incomingCancelled.set(true) },
        )
        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertTrue(closeStarted.get())
        assertTrue(incomingCancelled.get())
        assertTrue(elapsedMs < 2_000, "teardown must be bounded, took $elapsedMs ms")
    }
}

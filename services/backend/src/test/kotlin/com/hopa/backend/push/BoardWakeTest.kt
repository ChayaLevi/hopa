package com.hopa.backend.push

import java.util.concurrent.atomic.AtomicLong
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Board wakes in [GuardedPush]: their own budget, coalesced to the latest seq per device. */
class BoardWakeTest {
    private val clock = AtomicLong(1_763_300_000_000)
    private val sent = mutableListOf<Map<String, String>>()
    private val push = GuardedPush(
        sender = { token, data ->
            sent += data + ("token" to token)
            PushOutcome.Delivered
        },
        clock = clock::get,
    )

    /** A one-second window, so six wakes fit well inside the minute the budget counts. */
    private val shortWindow = GuardedPush(
        sender = { token, data ->
            sent += data + ("token" to token)
            PushOutcome.Delivered
        },
        clock = clock::get,
        boardCoalesceMs = 1_000,
    )

    @Test
    fun `the first board wake goes out at once with scope and seq only`() {
        assertEquals(PushOutcome.Delivered, push.wakeBoard("dev-a", "family", 3, "fcm-a"))

        assertEquals(
            listOf(mapOf("type" to "board.changed", "scope" to "family", "seq" to "3", "token" to "fcm-a")),
            sent,
        )
    }

    @Test
    fun `a burst inside the window is held back and flushed once with the latest seq`() {
        push.wakeBoard("dev-a", "family", 1, "fcm-a")
        (2L..20L).forEach { seq ->
            assertEquals(PushOutcome.Skipped, push.wakeBoard("dev-a", if (seq % 2 == 0L) "parents" else "family", seq, "fcm-a"))
        }
        assertEquals(0, push.flushBoardWakes(), "the window has not passed")

        clock.addAndGet(10_000)
        assertEquals(1, push.flushBoardWakes())
        assertEquals(listOf("1", "20"), sent.map { it["seq"] })
        assertEquals("parents", sent.last()["scope"])
        assertEquals(0, push.flushBoardWakes(), "nothing is sent twice")
    }

    @Test
    fun `a seq the device was already woken for is skipped`() {
        push.wakeBoard("dev-a", "family", 5, "fcm-a")
        clock.addAndGet(60_000)

        assertEquals(PushOutcome.Skipped, push.wakeBoard("dev-a", "family", 4, "fcm-a"))
        assertEquals(1, sent.size)
    }

    @Test
    fun `devices are coalesced separately`() {
        push.wakeBoard("dev-a", "family", 1, "fcm-a")
        push.wakeBoard("dev-b", "family", 1, "fcm-b")

        assertEquals(listOf("fcm-a", "fcm-b"), sent.map { it["token"] })
    }

    @Test
    fun `a spent board budget never touches the task wakes`() {
        repeat(6) { index ->
            assertEquals(PushOutcome.Delivered, shortWindow.wakeBoard("dev-a", "family", index + 1L, "fcm-a"))
            clock.addAndGet(1_000)
        }
        assertEquals(PushOutcome.Skipped, shortWindow.wakeBoard("dev-a", "family", 7, "fcm-a"), "board budget spent")

        repeat(6) { index ->
            assertEquals(PushOutcome.Delivered, shortWindow.wake("dev-a", "task-$index", "trace", "fcm-a"))
        }
        assertEquals(6, sent.count { it.containsKey("taskId") })
    }

    @Test
    fun `a held wake waits for the board budget, then carries the latest seq`() {
        repeat(6) { index ->
            shortWindow.wakeBoard("dev-a", "family", index + 1L, "fcm-a")
            clock.addAndGet(1_000)
        }
        shortWindow.wakeBoard("dev-a", "family", 7, "fcm-a")
        shortWindow.wakeBoard("dev-a", "family", 8, "fcm-a")
        assertEquals(0, shortWindow.flushBoardWakes(), "still over budget")

        clock.addAndGet(60_000)
        assertEquals(1, shortWindow.flushBoardWakes())
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "8"), sent.map { it["seq"] })
    }
}

package com.hopa.sidecar

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The Engine adapter receives turns through Engine's `onTurnEnd` hook (R31).
 * It is built from lambdas so the test needs no model or host.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineTaskRunnerTest {
    @Test
    fun `a turn starts with a fresh id and resumes with the answer`() = runTest {
        var startedId = ""
        var startedGoal = ""
        val runner = EngineTaskRunner(
            startTurn = { id, goal ->
                startedId = id
                startedGoal = goal
            },
            newId = { "turn-1" },
        )

        val answer = async { runner.run("open settings") }
        runCurrent()

        assertEquals("turn-1", startedId)
        assertEquals("open settings", startedGoal)

        runner.complete(startedId, "opened settings")
        assertEquals("opened settings", answer.await())
    }

    @Test
    fun `cancelling the caller cancels the engine turn`() = runTest {
        var cancellations = 0
        var started = false
        val runner = EngineTaskRunner(
            startTurn = { _, _ -> started = true },
            cancelTurn = { cancellations++ },
            newId = { "turn-2" },
        )

        val job = launch { runner.run("open settings") }
        runCurrent()
        assertTrue(started)

        job.cancel()
        runCurrent()

        assertEquals(1, cancellations)
    }

    @Test
    fun `an end hook for an unknown turn is ignored`() = runTest {
        var started = false
        val runner = EngineTaskRunner(
            startTurn = { _, _ -> started = true },
            newId = { "turn-3" },
        )

        val answer = async { runner.run("open settings") }
        runCurrent()
        assertTrue(started)

        runner.complete("some-other-turn", "wrong")
        runner.complete("turn-3", "right")

        assertEquals("right", answer.await())
    }
}

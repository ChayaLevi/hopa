package com.hopa.decision

import com.hopa.contracts.wire.Action
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DecisionCoordinatorTest {
    @Test
    fun `off without shadow does not ask any layer`() = runTest {
        var calls = 0
        val layer = StubDecisionLayer("rules") {
            calls += 1
            LayerDecision(Verb.ACT, "read_only", 0.9, "acted")
        }
        val coordinator = DecisionCoordinator(DecisionRegistry(listOf(layer)))
        val run = coordinator.evaluate(context(), DecisionMode.Off)
        assertEquals(0, calls)
        assertTrue(run.votes.isEmpty())
        assertNull(run.applied)
    }

    @Test
    fun `automatic applies the default layer and records a vote`() = runTest {
        val sink = mutableListOf<DecisionRun>()
        val coordinator = DecisionCoordinator(
            DecisionRegistry(
                listOf(
                    StubDecisionLayer("rules") {
                        LayerDecision(Verb.ACT, "read_only", 0.9, "acted")
                    },
                ),
            ),
        )
        val run = coordinator.evaluate(
            context(),
            DecisionMode.Automatic,
            sink = DecisionVoteSink { _, value -> sink += value },
        )
        assertEquals(Verb.ACT, run.decision?.verb)
        assertEquals("rules", run.applied?.layerId)
        assertTrue(run.applied!!.applied)
        assertEquals(1, sink.size)
        assertEquals(1, sink.single().votes.size)
    }

    @Test
    fun `a failing layer falls back to no decision and is still counted`() = runTest {
        val failing = StubDecisionLayer("broken") { error("boom") }
        val coordinator = DecisionCoordinator(DecisionRegistry(listOf(failing)))
        val run = coordinator.evaluate(context(), DecisionMode.Automatic)
        assertNull(run.applied)
        assertNull(run.decision)
        assertEquals(1, run.votes.size)
        assertEquals("boom", run.votes.single().trace)
        assertEquals("layer_error", run.votes.single().reason)
        assertTrue(run.notice!!.contains("failed"))
    }

    @Test
    fun `shadow votes are recorded but never applied`() = runTest {
        val applied = StubDecisionLayer("rules") {
            LayerDecision(Verb.ACT, "read_only", 0.9, "acted")
        }
        val observer = StubDecisionLayer("laix", status = LayerStatus.SHADOW) {
            LayerDecision(Verb.VERIFY, "laix_verify", 0.7, "look first")
        }
        val coordinator = DecisionCoordinator(DecisionRegistry(listOf(applied, observer)))
        val run = coordinator.evaluate(context(), DecisionMode.Automatic, shadow = true)
        assertEquals("rules", run.applied?.layerId)
        assertEquals(2, run.votes.size)
        assertEquals(1, run.votes.count { it.applied })
        assertEquals(Verb.ACT, run.decision?.verb)
    }

    @Test
    fun `a cancelled turn is never given to a layer`() = runTest {
        var calls = 0
        val layer = StubDecisionLayer("rules") {
            calls += 1
            LayerDecision(Verb.ACT, "read_only", 0.9, "acted")
        }
        val coordinator = DecisionCoordinator(DecisionRegistry(listOf(layer)))
        val run = coordinator.evaluate(context().copy(cancelled = true), DecisionMode.Automatic)
        assertEquals(0, calls)
        assertNull(run.applied)
    }

    private fun context(): DecisionContext = DecisionContext(
        runId = "run-1",
        step = 2,
        action = Action(kind = "ui.observe"),
    )
}

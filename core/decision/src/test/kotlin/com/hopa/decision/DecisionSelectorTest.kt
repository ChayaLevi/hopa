package com.hopa.decision

import com.hopa.contracts.wire.Action
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DecisionSelectorTest {
    private val rules = StubDecisionLayer("rules")
    private val shadow = StubDecisionLayer("laix", status = LayerStatus.SHADOW)
    private val untested = StubDecisionLayer("untested", conformance = ConformanceStatus.NOT_RUN)
    private val changeOnly = StubDecisionLayer("change-only", decidesUpTo = ActionClass.CHANGE)
    private val retired = StubDecisionLayer("old", status = LayerStatus.RETIRED)
    private val registry = DecisionRegistry(listOf(rules, shadow, untested, changeOnly, retired))
    private val selector = DecisionSelector(registry)

    @Test
    fun `off chooses nothing`() {
        assertNull(selector.select(DecisionMode.Off, ActionClass.READ).layer)
    }

    @Test
    fun `automatic picks the first allowed default`() {
        assertEquals("rules", selector.select(DecisionMode.Automatic, ActionClass.CHANGE).layer?.metadata?.id)
    }

    @Test
    fun `a retired layer never votes`() {
        assertTrue(registry.allowedFor(ActionClass.READ).none { it.metadata.id == "old" })
    }

    @Test
    fun `an untested layer is never allowed`() {
        assertTrue(registry.allowedFor(ActionClass.READ).none { it.metadata.id == "untested" })
    }

    @Test
    fun `pinning an unknown layer falls back with a notice`() {
        val selection = selector.select(DecisionMode.Pinned("missing"), ActionClass.READ)
        assertEquals("rules", selection.layer?.metadata?.id)
        assertNotNull(selection.notice)
        assertTrue(selection.notice!!.contains("not in this build"))
    }

    @Test
    fun `pinning a layer outside its class falls back with a notice`() {
        val selection = selector.select(DecisionMode.Pinned("change-only"), ActionClass.DANGEROUS)
        assertEquals("rules", selection.layer?.metadata?.id)
        assertTrue(selection.notice!!.contains("dangerous"))
    }

    @Test
    fun `pinning an untested layer falls back with a notice`() {
        val selection = selector.select(DecisionMode.Pinned("untested"), ActionClass.READ)
        assertEquals("rules", selection.layer?.metadata?.id)
        assertTrue(selection.notice!!.contains("conformance"))
    }

    @Test
    fun `pinning an allowed layer uses it`() {
        val selection = selector.select(DecisionMode.Pinned("change-only"), ActionClass.CHANGE)
        assertEquals("change-only", selection.layer?.metadata?.id)
        assertNull(selection.notice)
    }

    @Test
    fun `dangerous actions always keep the approval step`() {
        assertTrue(selector.select(DecisionMode.Automatic, ActionClass.DANGEROUS).requiresApproval)
        assertFalse(selector.select(DecisionMode.Automatic, ActionClass.READ).requiresApproval)
    }

    @Test
    fun `shadow voters include every allowed layer once`() {
        val voters = selector.voters(DecisionMode.Automatic, ActionClass.READ, shadow = true)
        val ids = voters.map { it.metadata.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals("rules", ids.first())
        assertTrue(ids.contains("laix"))
        assertFalse(ids.contains("untested"))
        assertFalse(ids.contains("old"))
    }

    @Test
    fun `off with shadow still votes`() = runTest {
        val calls = mutableListOf<String>()
        val counting = StubDecisionLayer("counting") { context ->
            calls += context.runId
            LayerDecision(Verb.VERIFY, "counting", 0.9, "counted")
        }
        val coordinator = DecisionCoordinator(DecisionRegistry(listOf(counting)))
        val run = coordinator.evaluate(context(), DecisionMode.Off, shadow = true)
        assertEquals(1, calls.size)
        assertNull(run.applied)
        assertEquals(1, run.votes.size)
    }

    @Test
    fun `duplicate ids are refused`() {
        val failure = runCatching { DecisionRegistry(listOf(rules, StubDecisionLayer("rules"))) }
        assertTrue(failure.isFailure)
        assertTrue(failure.exceptionOrNull()!!.message!!.contains("duplicate"))
    }

    private fun context(): DecisionContext = DecisionContext(
        runId = "run-1",
        step = 0,
        action = Action(kind = "ui.observe"),
    )
}

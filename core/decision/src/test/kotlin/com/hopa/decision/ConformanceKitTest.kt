package com.hopa.decision

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConformanceKitTest {
    private val kit = ConformanceKit()

    @Test
    fun `a careful stub passes every case`() = runTest {
        val report = kit.run(carefulStub())
        assertTrue(report.passed, report.violations.joinToString { "${it.caseId}: ${it.detail}" })
        assertEquals(ConformanceStatus.PASSED, report.status)
        assertEquals(ConformanceCases.standard().size, report.cases)
    }

    @Test
    fun `a layer that always acts fails the dangerous and empty cases`() = runTest {
        val alwaysAct = StubDecisionLayer("always-act") {
            LayerDecision(Verb.ACT, "always_act", 0.99, "acted")
        }
        val report = kit.run(alwaysAct)
        assertFalse(report.passed)
        assertEquals(ConformanceStatus.FAILED, report.status)
        val cases = report.violations.map { it.caseId }.toSet()
        assertTrue("empty-action" in cases)
        assertTrue("unknown-kind" in cases)
        assertTrue("payment" in cases)
        assertTrue("delete" in cases)
        assertTrue("cancelled" in cases)
        assertTrue(report.violations.all { it.rule == "verb" })
    }

    @Test
    fun `an act below the confidence floor is refused`() = runTest {
        val unsure = StubDecisionLayer("unsure") {
            LayerDecision(Verb.ACT, "guess", 0.3, "we might be fine")
        }
        val report = kit.run(unsure)
        assertTrue(report.violations.any { it.rule == "confidence" })
    }

    @Test
    fun `a layer that throws is a failure with its message`() = runTest {
        val broken = StubDecisionLayer("broken") { error("kaboom") }
        val report = kit.run(broken)
        assertFalse(report.passed)
        assertEquals(report.cases, report.violations.count { it.rule == "error" })
        assertTrue(report.violations.first().detail.contains("kaboom"))
    }

    private fun carefulStub(): DecisionLayer = StubDecisionLayer("careful") { context ->
        val verb = when {
            context.cancelled -> Verb.WAIT
            context.atStepLimit -> Verb.ASK
            context.action == null -> Verb.ASK
            context.action.kindOrNull() == null -> Verb.ASK
            context.attention.isSpent() -> Verb.WAIT
            context.actionClass == ActionClass.DANGEROUS -> Verb.ASK
            else -> Verb.VERIFY
        }
        LayerDecision(verb, "careful_${verb.name.lowercase()}", 0.9, "careful stub")
    }
}

package com.hopa.decision.laix

import com.hopa.contracts.wire.Action
import com.hopa.decision.ComparisonHarness
import com.hopa.decision.ConformanceKit
import com.hopa.decision.DecisionCase
import com.hopa.decision.DecisionContext
import com.hopa.decision.DecisionCorpus
import com.hopa.decision.Verb
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.laix.decision.contracts.Verb as LaixVerb

class LaixDecisionLayerTest {
    private val layer = LaixDecisionLayer()

    @Test
    fun `the LAIX adapter passes the conformance kit`() = runTest {
        val report = ConformanceKit().run(layer)
        assertTrue(report.passed, report.violations.joinToString { "${it.caseId}: ${it.detail}" })
    }

    @Test
    fun `LAIX matches every corpus reference`() = runTest {
        val cases = corpus()
        val row = ComparisonHarness(cases).run(listOf(layer)).rows.single()
        assertEquals(cases.size, row.agreement, row.disagreements.joinToString())
        assertEquals(cases.size, row.allowed)
        assertEquals(0, row.dangerousActs)
        assertEquals(0, row.errors)
    }

    @Test
    fun `a read is acted on and a change asks`() = runTest {
        assertEquals(Verb.ACT, layer.decide(context(Action(kind = "ui.observe"))).verb)
        assertEquals(Verb.ASK, layer.decide(context(Action(kind = "ui.tap", ref = "e1"))).verb)
    }

    @Test
    fun `money is never an act option`() = runTest {
        val decision = layer.decide(context(Action(kind = "payment.pay")))
        assertEquals(Verb.ASK, decision.verb)
        assertTrue(decision.reason.startsWith("laix."))
        assertTrue(decision.trace.startsWith("laix "))
    }

    @Test
    fun `LAIX verbs map to HOPA verbs`() {
        assertEquals(Verb.VERIFY, LaixVerbs.toHopa(LaixVerb.OBSERVE))
        assertEquals(Verb.ASK, LaixVerbs.toHopa(LaixVerb.GUIDE))
        assertEquals(Verb.ESCALATE, LaixVerbs.toHopa(LaixVerb.ESCALATE))
        assertEquals(Verb.ACT, LaixVerbs.toHopa(LaixVerb.ACT))
    }

    private fun context(action: Action?): DecisionContext = DecisionContext(
        runId = "run-1",
        step = 1,
        action = action,
        screen = "test screen",
    )

    private fun corpus(): List<DecisionCase> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/decisions/corpus.v1.json"))
        return stream.use { DecisionCorpus.read(it.readBytes().decodeToString()) }
    }
}

package com.hopa.decision.rules

import com.hopa.contracts.wire.Action
import com.hopa.decision.ActionCandidate
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

class RulesDecisionLayerTest {
    private val layer = RulesDecisionLayer()

    @Test
    fun `the rules layer passes the conformance kit`() = runTest {
        val report = ConformanceKit().run(layer)
        assertTrue(report.passed, report.violations.joinToString { "${it.caseId}: ${it.detail}" })
    }

    @Test
    fun `the rules layer matches every corpus reference`() = runTest {
        val cases = corpus()
        assertEquals(17, cases.size)
        val report = ComparisonHarness(cases).run(listOf(layer))
        val row = report.rows.single { it.layerId == "rules" }
        assertEquals(cases.size, row.agreement, row.disagreements.joinToString())
        assertEquals(cases.size, row.allowed)
        assertEquals(0, row.dangerousActs)
        assertEquals(0, row.errors)
    }

    @Test
    fun `a read acts and a change asks`() = runTest {
        assertEquals(Verb.ACT, layer.decide(context(Action(kind = "ui.observe"))).verb)
        assertEquals(Verb.ASK, layer.decide(context(Action(kind = "ui.tap", ref = "e1"))).verb)
    }

    @Test
    fun `payments and deletes ask with their own reason`() = runTest {
        val payment = layer.decide(context(Action(kind = "payment.pay")))
        assertEquals(Verb.ASK, payment.verb)
        assertEquals("payment_requires_approval", payment.reason)

        val delete = layer.decide(context(Action(kind = "system.delete")))
        assertEquals(Verb.ASK, delete.verb)
        assertEquals("destructive_requires_approval", delete.reason)
    }

    @Test
    fun `a repeated step verifies instead of acting`() = runTest {
        val action = Action(kind = "ui.tap", ref = "e1")
        val signature = com.hopa.decision.ActionSignature.of(action)
        val decision = layer.decide(
            context(action, history = listOf(signature, signature, signature)),
        )
        assertEquals(Verb.VERIFY, decision.verb)
        assertEquals("repeated_step", decision.reason)
    }

    @Test
    fun `an approval already given lets the step act`() = runTest {
        val action = Action(kind = "ui.tap", ref = "e1", text = "Wi-Fi")
        val signature = com.hopa.decision.ActionSignature.of(action)
        val decision = layer.decide(context(action, approvals = listOf(signature)))
        assertEquals(Verb.ACT, decision.verb)
        assertEquals("already_approved", decision.reason)
    }

    @Test
    fun `a cancelled turn waits`() = runTest {
        val decision = layer.decide(
            context(Action(kind = "ui.tap"), cancelled = true),
        )
        assertEquals(Verb.WAIT, decision.verb)
    }

    @Test
    fun `one safe read among several is chosen`() = runTest {
        val launch = ActionCandidate(
            "launch",
            Action(kind = "ui.launch", app = "com.android.settings"),
        )
        val delete = ActionCandidate("delete", Action(kind = "system.delete", ref = "e9"))

        val decision = layer.decide(context(null, candidates = listOf(launch, delete)))

        assertEquals(Verb.ACT, decision.verb)
        assertEquals("launch", decision.chosen)
        assertEquals("edge_read", decision.reason)
    }

    @Test
    fun `two equally plausible reads ask instead of guessing`() = runTest {
        val back = ActionCandidate("back", Action(kind = "ui.back"))
        val home = ActionCandidate("home", Action(kind = "ui.home"))

        val decision = layer.decide(context(null, candidates = listOf(back, home)))

        assertEquals(Verb.ASK, decision.verb)
        assertEquals("ambiguous_edges", decision.reason)
    }

    @Test
    fun `one change edge asks with the edge named`() = runTest {
        val wifi = ActionCandidate("wifi", Action(kind = "ui.tap", ref = "e1", text = "Wi-Fi"))
        val delete = ActionCandidate("delete", Action(kind = "system.delete", ref = "e9"))

        val decision = layer.decide(context(null, candidates = listOf(wifi, delete)))

        assertEquals(Verb.ASK, decision.verb)
        assertEquals("edge_needs_approval", decision.reason)
        assertEquals("wifi", decision.chosen)
    }

    @Test
    fun `an edge that failed twice is not chosen again`() = runTest {
        val dead = ActionCandidate(
            "dead",
            Action(kind = "ui.observe"),
            failures = 2,
        )
        val delete = ActionCandidate("delete", Action(kind = "system.delete", ref = "e9"))

        val decision = layer.decide(context(null, candidates = listOf(dead, delete)))

        assertEquals(Verb.VERIFY, decision.verb)
        assertEquals("no_fresh_edge", decision.reason)
    }

    private fun context(
        action: Action?,
        history: List<String> = emptyList(),
        approvals: List<String> = emptyList(),
        cancelled: Boolean = false,
        candidates: List<ActionCandidate> = emptyList(),
    ): DecisionContext = DecisionContext(
        runId = "run-1",
        step = 1,
        action = action,
        screen = "test screen",
        history = history,
        approvals = approvals,
        cancelled = cancelled,
        candidates = candidates,
    )

    private fun corpus(): List<DecisionCase> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/decisions/corpus.v1.json")) {
            "the corpus is not on the test classpath"
        }
        return stream.use { DecisionCorpus.read(it.readBytes().decodeToString()) }
    }
}

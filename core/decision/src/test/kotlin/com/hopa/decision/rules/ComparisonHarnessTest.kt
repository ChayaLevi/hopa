package com.hopa.decision.rules

import com.hopa.decision.ActionClass
import com.hopa.decision.ComparisonHarness
import com.hopa.decision.ConformanceStatus
import com.hopa.decision.DecisionContext
import com.hopa.decision.DecisionCorpus
import com.hopa.decision.DecisionLayer
import com.hopa.decision.LatencyClass
import com.hopa.decision.LayerDecision
import com.hopa.decision.LayerMetadata
import com.hopa.decision.LayerStatus
import com.hopa.decision.Verb
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ComparisonHarnessTest {
    @Test
    fun `the table carries a row per layer and its ratios`() = runTest {
        val cases = corpus()
        val text = ComparisonHarness(cases).run(listOf(RulesDecisionLayer())).toMarkdown()
        assertTrue(text.contains("| rules | 1.0.0 |"), text)
        assertTrue(text.contains("100% (17/17)"), text)
        assertTrue(text.contains("Dangerous ACT"), text)
    }

    @Test
    fun `a broken layer becomes an error row instead of a crash`() = runTest {
        val broken = object : DecisionLayer {
            override val metadata = LayerMetadata(
                id = "broken",
                version = "0.0.1",
                latency = LatencyClass.MILLI,
                decidesUpTo = ActionClass.READ,
                status = LayerStatus.EXPERIMENTAL,
                conformance = ConformanceStatus.NOT_RUN,
                author = "test",
                summary = "fails every call",
            )

            override suspend fun decide(context: DecisionContext): LayerDecision = error("no")
        }
        val report = ComparisonHarness(corpus()).run(listOf(broken))
        val row = report.rows.single()
        assertEquals(17, row.errors)
        assertEquals(0, row.agreement)
        assertEquals(0, row.dangerousActs)
        assertTrue(row.disagreements.all { it.contains("error") })
    }

    @Test
    fun `the model baseline row appears only when the corpus recorded it`() = runTest {
        val plain = ComparisonHarness(corpus()).run(listOf(RulesDecisionLayer()))
        assertNull(plain.rows.firstOrNull { it.layerId == "model (recorded)" })

        val recorded = DecisionCorpus.read(
            """
            {
              "schemaVersion": "1",
              "cases": [
                {
                  "id": "launch",
                  "action": { "kind": "ui.launch", "app": "com.android.settings" },
                  "expected": "act",
                  "allowed": ["act", "verify"],
                  "model": "act"
                }
              ]
            }
            """.trimIndent(),
        )
        val report = ComparisonHarness(recorded).run(listOf(RulesDecisionLayer()))
        val model = report.rows.single { it.layerId == "model (recorded)" }
        assertEquals(1, model.agreement)
        assertEquals(Verb.ACT, recorded.single().model)
    }

    private fun corpus(): List<com.hopa.decision.DecisionCase> {
        val stream = checkNotNull(javaClass.getResourceAsStream("/decisions/corpus.v1.json"))
        return stream.use { DecisionCorpus.read(it.readBytes().decodeToString()) }
    }
}

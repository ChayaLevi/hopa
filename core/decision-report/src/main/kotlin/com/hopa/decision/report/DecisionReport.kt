package com.hopa.decision.report

import com.hopa.decision.ComparisonHarness
import com.hopa.decision.DecisionCorpus
import com.hopa.decision.laix.LaixDecisionLayer
import com.hopa.decision.rules.RulesDecisionLayer
import kotlinx.coroutines.runBlocking

/**
 * Prints the comparison table for every layer this build ships.
 *
 * `gradlew :decision-report:decisionReport` from `apps/android/`, or pass a
 * corpus path as the first argument. Adding a layer is one module and one line
 * in the list below.
 */
object DecisionReport {
    @JvmStatic
    fun main(args: Array<String>) {
        val path = java.nio.file.Path.of(args.firstOrNull() ?: "spec/decisions/corpus.v1.json")
        val cases = DecisionCorpus.readFrom(path)
        val layers = listOf(RulesDecisionLayer(), LaixDecisionLayer())
        val report = runBlocking { ComparisonHarness(cases).run(layers) }
        println(report.toMarkdown())
    }
}

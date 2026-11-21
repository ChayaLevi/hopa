package com.hopa.decision.rules

import com.hopa.decision.ComparisonHarness
import com.hopa.decision.DecisionCorpus
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

/**
 * Prints the comparison table for every layer this build ships.
 *
 * `gradlew :decision-rules:decisionReport` from `apps/android/`, or pass a
 * corpus path as the first argument.
 */
object DecisionReport {
    @JvmStatic
    fun main(args: Array<String>) {
        val path = Path.of(args.firstOrNull() ?: "spec/decisions/corpus.v1.json")
        val cases = DecisionCorpus.readFrom(path)
        val report = runBlocking {
            ComparisonHarness(cases).run(listOf(RulesDecisionLayer()))
        }
        println(report.toMarkdown())
    }
}

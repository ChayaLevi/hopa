package com.hopa.decision

/**
 * Runs any layer over the corpus and reports how it did.
 *
 * The harness is part of the product, not a one-off script: the same cases are
 * used in CI and when deciding whether a layer may become the default. It also
 * prints the recorded model-only answers as another row, so "a layer is better"
 * is a comparison against the alternative, not against nothing.
 */
class ComparisonHarness(
    private val cases: List<DecisionCase>,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    init {
        require(cases.isNotEmpty()) { "the comparison corpus is empty" }
        require(cases.map { it.id }.toSet().size == cases.size) { "duplicate case ids in the corpus" }
    }

    suspend fun run(layers: List<DecisionLayer>): ComparisonReport {
        val rows = mutableListOf<ComparisonRow>()
        for (layer in layers) {
            rows += measure(layer.metadata.id, layer.metadata.version) { case ->
                layer.decide(case.context())
            }
        }
        modelRow()?.let { rows += it }
        return ComparisonReport(rows = rows, cases = cases)
    }

    private suspend fun measure(
        layerId: String,
        version: String,
        decide: suspend (DecisionCase) -> LayerDecision,
    ): ComparisonRow {
        var agreement = 0
        var allowed = 0
        var dangerous = 0
        var trace = 0
        var errors = 0
        var confidence = 0.0
        val durations = mutableListOf<Long>()
        val disagreements = mutableListOf<String>()
        for (case in cases) {
            val started = nanoTime()
            val decision = try {
                decide(case)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                errors += 1
                disagreements += "${case.id} (error: ${error::class.simpleName})"
                durations += ((nanoTime() - started) / 1_000).coerceAtLeast(0)
                continue
            }
            durations += ((nanoTime() - started) / 1_000).coerceAtLeast(0)
            confidence += decision.confidence
            if (decision.trace.isNotBlank()) {
                trace += 1
            }
            if (decision.verb == case.expected) {
                agreement += 1
            } else {
                disagreements += "${case.id} (expected ${case.expected.name.lowercase()}, " +
                    "got ${decision.verb.name.lowercase()})"
            }
            if (decision.verb in case.allowed) {
                allowed += 1
            }
            if (case.context().actionClass == ActionClass.DANGEROUS && decision.verb == Verb.ACT) {
                dangerous += 1
            }
        }
        val answered = (cases.size - errors).coerceAtLeast(1)
        return ComparisonRow(
            layerId = layerId,
            version = version,
            cases = cases.size,
            agreement = agreement,
            allowed = allowed,
            dangerousActs = dangerous,
            p50Micros = Percentiles.of(durations, 50),
            p95Micros = Percentiles.of(durations, 95),
            meanConfidence = confidence / answered,
            traceComplete = trace,
            errors = errors,
            disagreements = disagreements,
        )
    }

    /** The model-only path as recorded in the corpus, when it was recorded. */
    private fun modelRow(): ComparisonRow? {
        val recorded = cases.filter { it.model != null }
        if (recorded.isEmpty()) {
            return null
        }
        return ComparisonRow(
            layerId = "model (recorded)",
            version = "corpus",
            cases = recorded.size,
            agreement = recorded.count { it.model == it.expected },
            allowed = recorded.count { it.model in it.allowed },
            dangerousActs = recorded.count {
                it.context().actionClass == ActionClass.DANGEROUS && it.model == Verb.ACT
            },
            p50Micros = 0,
            p95Micros = 0,
            meanConfidence = 0.0,
            traceComplete = 0,
            errors = 0,
        )
    }
}

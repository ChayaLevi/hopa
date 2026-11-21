package com.hopa.decision

/**
 * Runs the standard cases against one layer.
 *
 * The kit is plain JVM code on purpose: every adapter, including a vendored
 * one, is held to the same rules in a normal unit test, without a phone.
 */
class ConformanceKit(
    cases: List<ConformanceCase> = ConformanceCases.standard(),
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val cases: List<ConformanceCase> = cases.toList()

    suspend fun run(layer: DecisionLayer): ConformanceReport {
        val violations = mutableListOf<ConformanceViolation>()
        val durations = mutableListOf<Long>()
        for (case in cases) {
            val started = nanoTime()
            val decision = try {
                layer.decide(case.context)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                durations += ((nanoTime() - started) / 1_000).coerceAtLeast(0)
                violations += ConformanceViolation(
                    caseId = case.id,
                    rule = "error",
                    detail = error.message.orEmpty().ifBlank { error::class.simpleName.orEmpty() },
                )
                continue
            }
            durations += ((nanoTime() - started) / 1_000).coerceAtLeast(0)
            if (decision.verb !in case.allowed) {
                violations += ConformanceViolation(
                    caseId = case.id,
                    rule = "verb",
                    detail = "returned ${decision.verb} (${decision.reason}); " +
                        "allowed ${case.allowed.joinToString { it.name.lowercase() }}: ${case.why}",
                )
            }
            if (decision.verb == Verb.ACT && decision.confidence < ACT_FLOOR) {
                violations += ConformanceViolation(
                    caseId = case.id,
                    rule = "confidence",
                    detail = "ACT at confidence ${decision.confidence} (floor $ACT_FLOOR)",
                )
            }
        }
        return ConformanceReport(
            layerId = layer.metadata.id,
            layerVersion = layer.metadata.version,
            cases = cases.size,
            violations = violations,
            p95Micros = percentile(durations, 95),
        )
    }

    private fun percentile(values: List<Long>, percent: Int): Long {
        if (values.isEmpty()) {
            return 0
        }
        val sorted = values.sorted()
        val index = ((sorted.size * percent + 99) / 100 - 1).coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    private companion object {
        /** Below this an ACT is a guess, and a guess may not move the phone. */
        const val ACT_FLOOR = 0.5
    }
}

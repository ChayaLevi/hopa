package com.hopa.decision

/** A layer whose answer and metadata a test chooses. */
class StubDecisionLayer(
    id: String,
    status: LayerStatus = LayerStatus.DEFAULT,
    conformance: ConformanceStatus = ConformanceStatus.PASSED,
    decidesUpTo: ActionClass = ActionClass.DANGEROUS,
    private val answer: suspend (DecisionContext) -> LayerDecision = {
        LayerDecision(Verb.ASK, "stub_ask", 0.9, "stub")
    },
) : DecisionLayer {
    override val metadata: LayerMetadata = LayerMetadata(
        id = id,
        version = "0.0.1",
        latency = LatencyClass.MICRO,
        decidesUpTo = decidesUpTo,
        status = status,
        conformance = conformance,
        author = "test",
        summary = "A stub layer for tests.",
    )

    override suspend fun decide(context: DecisionContext): LayerDecision = answer(context)
}

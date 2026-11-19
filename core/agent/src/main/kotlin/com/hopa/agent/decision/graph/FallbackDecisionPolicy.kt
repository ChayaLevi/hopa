package com.hopa.agent.decision.graph
import com.hopa.agent.decision.Decision
import com.hopa.agent.decision.DecisionInput
import com.hopa.agent.decision.DecisionOutcome
import com.hopa.agent.decision.DecisionPolicy

/**
 * The graph policy: on the screen it recognizes, take the best guarded edge. When
 * the screen is unknown or no edge applies, it says [Decision.NoPath] and leaves
 * the choice to a fallback policy instead of guessing a tap.
 */
class FallbackDecisionPolicy(
    private val first: DecisionPolicy,
    private val second: DecisionPolicy,
) : DecisionPolicy {
    override val name: String = "${first.name}/${second.name}"

    override suspend fun decide(input: DecisionInput): DecisionOutcome {
        val outcome = first.decide(input)
        if (outcome.decision is Decision.NoPath) {
            return second.decide(input)
        }
        return outcome
    }
}

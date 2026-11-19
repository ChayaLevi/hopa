package com.hopa.agent.graph

import com.hopa.agent.Decision
import com.hopa.agent.DecisionInput
import com.hopa.agent.DecisionOutcome
import com.hopa.agent.DecisionPolicy

/**
 * The graph policy: on the screen it recognizes, take the best guarded edge. When
 * the screen is unknown or no edge applies, it says [Decision.NoPath] and leaves
 * the choice to a fallback policy instead of guessing a tap.
 */
class GraphDecisionPolicy(
    private val app: String,
    private val graph: () -> NavGraph?,
) : DecisionPolicy {
    override val name: String = "graph"

    override suspend fun decide(input: DecisionInput): DecisionOutcome {
        val screen = input.observation ?: return DecisionOutcome(Decision.NoPath)
        val current = graph() ?: return DecisionOutcome(Decision.NoPath)
        val key = ScreenKey.of(app, screen)
        val edge = current.edgesFrom(key)
            .filter { it.guard.matches(screen) }
            .maxByOrNull { it.successRate }
            ?: return DecisionOutcome(Decision.NoPath)
        return DecisionOutcome(Decision.Act(edge.action))
    }
}

/**
 * Try [first]; when it knows no safe step, ask [second]. This is how a graph can
 * be learned app by app without changing the turn: the model still answers where
 * the graph has nothing.
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

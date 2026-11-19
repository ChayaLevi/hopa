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

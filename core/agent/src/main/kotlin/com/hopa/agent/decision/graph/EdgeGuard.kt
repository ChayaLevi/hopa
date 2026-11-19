package com.hopa.agent.decision.graph

import com.hopa.contracts.wire.Observation
import com.hopa.contracts.wire.ObservationNode
import com.hopa.observation.tree.NodeAnchor

/**
 * What has to be true on the screen for an edge to be taken. A graph without
 * guards is a list of "tap something"; a guarded edge either matches the screen
 * it was learned on or stays out of the way.
 */
sealed interface EdgeGuard {
    fun matches(observation: Observation): Boolean

    data object Always : EdgeGuard {
        override fun matches(observation: Observation): Boolean = true
    }

    /** Some node on the screen carries this text or description. */
    data class TextVisible(val text: String) : EdgeGuard {
        override fun matches(observation: Observation): Boolean = anyNode(observation.tree) { node ->
            node.text.contains(text, ignoreCase = true) ||
                node.description.contains(text, ignoreCase = true)
        }
    }

    /** The screen still has the node a recipe-style anchor points at. */
    data class AnchorVisible(val anchor: String) : EdgeGuard {
        override fun matches(observation: Observation): Boolean = NodeAnchor.refOf(observation.tree, anchor) != null
    }

    data class All(val guards: List<EdgeGuard>) : EdgeGuard {
        override fun matches(observation: Observation): Boolean = guards.all { it.matches(observation) }
    }

    companion object {
        fun text(text: String): EdgeGuard = TextVisible(text)

        fun anchor(anchor: String): EdgeGuard = AnchorVisible(anchor)

        private fun anyNode(node: ObservationNode, predicate: (ObservationNode) -> Boolean): Boolean =
            predicate(node) || node.children.any { anyNode(it, predicate) }
    }
}

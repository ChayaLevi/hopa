package com.hopa.agent.goal

import com.hopa.contracts.wire.Observation
import com.hopa.contracts.wire.ObservationNode
import com.hopa.observation.tree.NodeAnchor

/**
 * Says whether the turn actually reached what the person asked for, instead of
 * trusting the model's "done".
 *
 * The check is made against the screen the turn ended on: a checkpoint is met
 * when a **visible** node carries its label or matches its anchor key. A node
 * that is hidden, or merely present in the tree below the fold, does not count -
 * the person cannot see it, so the turn did not finish the job.
 */
class GoalVerifier {
    fun verify(screen: Observation?, checkpoints: List<Checkpoint>): GoalVerification {
        if (checkpoints.isEmpty()) {
            return GoalVerification(emptyList(), emptyList())
        }
        val tree = screen?.tree
            ?: return GoalVerification(emptyList(), checkpoints)
        val (met, missing) = checkpoints.partition { checkpoint -> holds(tree, checkpoint.anchor) }
        return GoalVerification(met, missing)
    }

    private fun holds(node: ObservationNode, anchor: String): Boolean {
        if (node.visible && matches(node, anchor)) {
            return true
        }
        return node.children.any { child -> holds(child, anchor) }
    }

    private fun matches(node: ObservationNode, anchor: String): Boolean {
        if (NodeAnchor.key(node) == anchor) {
            return true
        }
        val wanted = normalise(anchor)
        return normalise(node.text) == wanted ||
            normalise(node.description) == wanted ||
            node.id == anchor
    }

    private fun normalise(value: String): String = value.trim().replace(Regex("\\s+"), " ").lowercase()
}

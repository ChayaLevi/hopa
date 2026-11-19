package com.hopa.observation.tree

import com.hopa.contracts.wire.ObservationNode

/**
 * What a node *is*, not where it sits. A ref (`e21`) is a position in one tree,
 * so replaying an action by ref taps whatever is at that position next time. The
 * anchor is the text, description and resource id of the control, which survive
 * a redraw and let a replay find the same control again.
 *
 * A node with no text, description or id has no anchor: it can only be found by
 * position, so a replay must not use it.
 */
object NodeAnchor {
    fun key(node: ObservationNode): String? {
        val parts = buildList {
            node.text.takeIf { it.isNotBlank() }?.let { add("text=" + normalize(it)) }
            node.description.takeIf { it.isNotBlank() }?.let { add("desc=" + normalize(it)) }
            node.id.takeIf { it.isNotBlank() }?.let { add("id=" + it) }
        }
        if (parts.isEmpty()) {
            return null
        }
        return node.type.ifBlank { "node" } + "|" + parts.joinToString("|")
    }

    /** The node a ref points at, in this tree. */
    fun find(tree: ObservationNode, ref: String): ObservationNode? {
        if (tree.ref == ref) return tree
        for (child in tree.children) {
            find(child, ref)?.let { return it }
        }
        return null
    }

    /**
     * The ref of the node the anchor describes, preferring one the person can
     * actually use. Null when nothing matches, and the caller must then stop
     * rather than tap a guess.
     */
    fun refOf(tree: ObservationNode, anchor: String): String? =
        firstMatch(tree, anchor) { node -> node.visible && node.enabled }
            ?: firstMatch(tree, anchor) { true }

    private fun firstMatch(node: ObservationNode, anchor: String, usable: (ObservationNode) -> Boolean): String? {
        if (node.ref.isNotBlank() && usable(node) && key(node) == anchor) {
            return node.ref
        }
        for (child in node.children) {
            firstMatch(child, anchor, usable)?.let { return it }
        }
        return null
    }

    private fun normalize(value: String): String = value.trim().replace(Regex("\\s+"), " ").lowercase()
}

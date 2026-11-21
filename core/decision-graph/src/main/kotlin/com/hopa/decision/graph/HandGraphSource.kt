package com.hopa.decision.graph

import com.hopa.contracts.wire.Action
import com.hopa.contracts.wire.Observation
import com.hopa.contracts.wire.ObservationNode

/**
 * Follows a hand-written graph for a few known apps.
 *
 * The matcher is deliberately literal: the current screen must show one of a
 * node's strings, and the control to tap must be on that screen. A miss returns
 * null, so the caller falls back to the model. It never invents an edge.
 */
class HandGraphSource(private val graphs: List<AppGraph>) : GraphStepSource {
    override fun next(observation: Observation, goal: String): GraphStep? {
        val wanted = normalize(goal)
        if (wanted.isEmpty()) return null
        for (graph in graphs) {
            val inApp = observation.app.equals(graph.app, ignoreCase = true)
            // A node means "this is the screen of this app". Words from another
            // window (a chat history that mentions Settings, say) must never
            // choose a node, or the graph would tap a control that is not there.
            val node = if (inApp) nodeFor(graph, observation.tree) else null
            val from = when {
                node != null -> node.id
                inApp -> ANY
                else -> START
            }
            val matching = graph.edges.filter { edge ->
                edge.from == from && edge.goal.any { keyword -> wanted.contains(normalize(keyword)) }
            }
            // Try every matching edge: a Settings page may show Wi-Fi directly or
            // hide it behind Connections, and only the current screen decides which.
            for (edge in matching) {
                val action = resolve(edge.action, graph, observation) ?: continue
                return GraphStep(
                    graph = graph.name,
                    node = from,
                    action = action,
                    answer = edge.answer,
                    done = edge.done,
                )
            }
        }
        return null
    }

    override fun target(goal: String): GraphTarget? {
        val wanted = normalize(goal)
        if (wanted.isEmpty()) return null
        for (graph in graphs) {
            val matching = graph.edges.filter { edge ->
                edge.goal.any { keyword -> wanted.contains(normalize(keyword)) }
            }
            val starts = matching.any { edge -> edge.from == START }
            if (starts) {
                // A goal the graph can finish deserves the first try, before a
                // one-shot command: the receipt then says Graph, and the steps
                // are the written ones, not a launch plus a model.
                return GraphTarget(
                    app = graph.app,
                    name = graph.name,
                    completes = matching.any { edge -> edge.done },
                )
            }
        }
        return null
    }

    /**
     * The node the screen most looks like, by exact labels.
     *
     * Exact, not "anywhere on screen": a deep Settings page is titled "More
     * connection settings", which contains "settings" but is not the root, and a
     * substring match would send the graph to a control that is not there.
     *
     * The best-supported node wins, not the first label found: Samsung's
     * Connections page shows a Wi-Fi row near the top, so a first-match rule
     * would call it the Wi-Fi screen before seeing its other rows. Ties go to the
     * declared order, where the broader screen comes first.
     */
    private fun nodeFor(graph: AppGraph, root: ObservationNode): GraphNode? {
        val byName = graph.nodes
            .map { node -> node to node.texts.map(::normalize).filter { it.isNotEmpty() }.toSet() }
            .filter { (_, texts) -> texts.isNotEmpty() }
        if (byName.isEmpty()) return null
        val hits = mutableMapOf<GraphNode, MutableSet<String>>()
        fun visit(current: ObservationNode) {
            val labels = listOf(normalize(current.text), normalize(current.description))
                .filter { it.isNotEmpty() }
            for (label in labels) {
                for ((node, texts) in byName) {
                    if (label in texts) {
                        hits.getOrPut(node) { mutableSetOf() }.add(label)
                    }
                }
            }
            for (child in current.children) visit(child)
        }
        visit(root)
        var best: GraphNode? = null
        var bestCount = 0
        for (node in graph.nodes) {
            val count = hits[node]?.size ?: 0
            if (count > bestCount) {
                best = node
                bestCount = count
            }
        }
        return best
    }

    private fun resolve(action: GraphAction, graph: AppGraph, observation: Observation): Action? = when (action.kind) {
        "launch" -> Action(
            kind = "ui.launch",
            app = action.app.ifBlank { graph.app },
            timeoutMs = 8_000,
        )

        "tap" -> {
            // Tap by what was seen, not by a node handle: Settings can finish
            // drawing between the read and the tap, and a recycled handle would
            // fail on a control that is still right there.
            val point = findPoint(observation.tree, action.texts) ?: return null
            Action(kind = "ui.tap", x = point.first, y = point.second)
        }

        "back" -> Action(kind = "ui.back")

        "scroll" -> Action(
            kind = "ui.scroll",
            direction = action.direction.ifBlank { "down" },
        )

        else -> null
    }

    /**
     * The centre of the control that carries one of [texts].
     *
     * A tap only counts when something tappable carries the label: the node
     * itself, or the row around it. A group heading (Samsung's Settings root has
     * a "Connections" heading above Wi-Fi) is not a control, so a tap edge that
     * matches only a heading is a miss, and the next edge gets its turn.
     */
    private fun findPoint(node: ObservationNode, texts: List<String>): Pair<Int, Int>? {
        if (texts.isEmpty()) return null
        val wanted = texts.map(::normalize).filter { it.isNotEmpty() }
        var loose: Pair<Int, Int>? = null
        fun visit(current: ObservationNode, row: Pair<Int, Int>?): Pair<Int, Int>? {
            val point = center(current.bounds)
            val here = if (current.clickable) point else null
            val label = normalize("${current.text} ${current.description}")
            val exact = wanted.any { label == it }
            if (exact || wanted.any { label.contains(it) }) {
                val target = here ?: row
                if (target != null) {
                    // "Wi-Fi" must not become "Wi-Fi calling": an exact label wins
                    // over a longer one that merely contains the words.
                    if (exact) return target
                    if (loose == null) loose = target
                }
            }
            val nextRow = if (current.clickable && point != null) point else row
            for (child in current.children) {
                visit(child, nextRow)?.let { return it }
            }
            return null
        }
        visit(node, null)?.let { return it }
        return loose
    }

    /** The middle of a visible rectangle, or null when the node has no usable bounds. */
    private fun center(bounds: List<Int>?): Pair<Int, Int>? {
        if (bounds == null || bounds.size != 4) return null
        val left = bounds[0]
        val top = bounds[1]
        val right = bounds[2]
        val bottom = bounds[3]
        if (right <= left || bottom <= top) return null
        return (left + right) / 2 to (top + bottom) / 2
    }

    private fun normalize(text: String): String = text
        .lowercase()
        .replace('ё', 'е')
        .replace('\u2011', '-')
        .replace(Regex("\\s+"), " ")
        .trim()

    private companion object {
        const val START = "start"

        /** Any screen of this app that the graph does not name. */
        const val ANY = "any"
    }
}

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
        val visible = normalize(visibleText(observation))
        for (graph in graphs) {
            val inApp = observation.app.equals(graph.app, ignoreCase = true)
            val node = nodeFor(graph, visible)
            if (node == null && inApp) {
                // In the app but on an unknown screen: do not guess.
                continue
            }
            val from = node?.id ?: START
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

    private fun nodeFor(graph: AppGraph, visible: String): GraphNode? = graph.nodes.firstOrNull { node ->
        node.texts.any { text -> visible.contains(normalize(text)) }
    }

    private fun resolve(action: GraphAction, graph: AppGraph, observation: Observation): Action? = when (action.kind) {
        "launch" -> Action(
            kind = "ui.launch",
            app = action.app.ifBlank { graph.app },
            timeoutMs = 8_000,
        )

        "tap" -> {
            val ref = findRef(observation.tree, action.texts) ?: return null
            Action(kind = "ui.tap", ref = ref)
        }

        "back" -> Action(kind = "ui.back")

        "scroll" -> Action(
            kind = "ui.scroll",
            direction = action.direction.ifBlank { "down" },
        )

        else -> null
    }

    /** The first clickable node whose text or description names one of [texts]. */
    private fun findRef(node: ObservationNode, texts: List<String>): String? {
        if (texts.isEmpty()) return null
        val wanted = texts.map(::normalize).filter { it.isNotEmpty() }
        var fallback: String? = null
        fun visit(current: ObservationNode): String? {
            val label = normalize("${current.text} ${current.description}")
            val matches = wanted.any { label.contains(it) }
            if (matches && current.ref.isNotBlank()) {
                if (current.clickable) return current.ref
                if (fallback == null) fallback = current.ref
            }
            for (child in current.children) {
                visit(child)?.let { return it }
            }
            return null
        }
        visit(node)?.let { return it }
        return fallback
    }

    private fun visibleText(observation: Observation): String = buildString {
        append(observation.text)
        append(' ')
        fun visit(node: ObservationNode) {
            append(node.text)
            append(' ')
            append(node.description)
            append(' ')
            for (child in node.children) visit(child)
        }
        visit(observation.tree)
    }

    private fun normalize(text: String): String = text
        .lowercase()
        .replace('ё', 'е')
        .replace('\u2011', '-')
        .replace(Regex("\\s+"), " ")
        .trim()

    private companion object {
        const val START = "start"
    }
}

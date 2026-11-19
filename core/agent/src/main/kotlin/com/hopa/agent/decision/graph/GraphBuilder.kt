package com.hopa.agent.graph

import com.hopa.contracts.wire.RunRecord

/**
 * Turns verified runs into a graph. A step that executed without an error is one
 * observation of an edge; repeated runs raise its [NavEdge.successRate], so the
 * policy prefers the path that actually worked.
 */
object GraphBuilder {
    fun build(app: String, records: List<RunRecord>, base: NavGraph = NavGraph(app)): NavGraph {
        val nodes = base.nodes.toMutableList()
        val edges = base.edges.toMutableList()

        fun addNode(key: ScreenKey?) {
            if (key != null && nodes.none { it.key == key }) {
                nodes += NavNode(key)
            }
        }

        records.groupBy { it.runId }.values.forEach { run ->
            run.sortedBy { it.step }.forEach { record ->
                val from = ScreenKey.fromHash(app, record.observationHash)
                addNode(from)
                val to = record.afterHash
                    ?.takeIf { it.isNotBlank() }
                    ?.let { ScreenKey.fromHash(app, it) }
                addNode(to)

                val action = record.action
                val success = record.executed && record.error == null
                val index = edges.indexOfFirst {
                    it.from == from &&
                        it.action.kind == action.kind &&
                        it.action.ref == action.ref &&
                        it.action.text == action.text
                }
                if (index >= 0) {
                    val edge = edges[index]
                    edges[index] = edge.copy(
                        runs = edge.runs + 1,
                        successes = edge.successes + if (success) 1 else 0,
                        to = edge.to ?: to,
                    )
                } else {
                    edges += NavEdge(
                        from = from,
                        action = action,
                        to = to,
                        runs = 1,
                        successes = if (success) 1 else 0,
                    )
                }
            }
        }

        return NavGraph(
            app = app,
            version = base.version + 1,
            nodes = nodes,
            edges = edges,
        )
    }
}

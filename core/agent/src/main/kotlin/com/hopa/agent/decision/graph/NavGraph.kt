package com.hopa.agent.decision.graph

import com.hopa.contracts.wire.Action

data class NavNode(
    val key: ScreenKey,
    val label: String = "",
)

/**
 * One thing that can be done on [from], and where it led. [guard] is required for
 * safety: an edge only applies to a screen that still looks like the one it was
 * learned on.
 */
data class NavEdge(
    val from: ScreenKey,
    val action: Action,
    val guard: EdgeGuard = EdgeGuard.Always,
    val to: ScreenKey? = null,
    val runs: Int = 0,
    val successes: Int = 0,
    val source: String = "learned",
) {
    val successRate: Double
        get() = if (runs == 0) 0.0 else successes.toDouble() / runs
}

/** What is reachable from where, per app. */
data class NavGraph(
    val app: String,
    val version: Int = 1,
    val nodes: List<NavNode> = emptyList(),
    val edges: List<NavEdge> = emptyList(),
) {
    fun node(key: ScreenKey): NavNode? = nodes.firstOrNull { it.key == key }

    fun edgesFrom(key: ScreenKey): List<NavEdge> = edges.filter { it.from == key }
}

/** Where a graph is kept: Room in the app, a file in the sidecar, memory in tests. */
interface NavGraphStore {
    suspend fun load(app: String): NavGraph?

    suspend fun save(graph: NavGraph)
}

class InMemoryNavGraphStore(initial: List<NavGraph> = emptyList()) : NavGraphStore {
    private val graphs = initial.associateBy { it.app }.toMutableMap()

    override suspend fun load(app: String): NavGraph? = graphs[app]

    override suspend fun save(graph: NavGraph) {
        graphs[graph.app] = graph
    }
}

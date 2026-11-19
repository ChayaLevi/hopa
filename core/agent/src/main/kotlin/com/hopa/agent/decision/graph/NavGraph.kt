package com.hopa.agent.decision.graph

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

package com.hopa.agent.decision.graph

class InMemoryNavGraphStore(initial: List<NavGraph> = emptyList()) : NavGraphStore {
    private val graphs = initial.associateBy { it.app }.toMutableMap()

    override suspend fun load(app: String): NavGraph? = graphs[app]

    override suspend fun save(graph: NavGraph) {
        graphs[graph.app] = graph
    }
}

package com.hopa.agent.decision.graph

interface NavGraphStore {
    suspend fun load(app: String): NavGraph?

    suspend fun save(graph: NavGraph)
}

package com.hopa.agent.decision.graph

import com.hopa.contracts.wire.Action

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

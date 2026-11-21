package com.hopa.decision.graph

import com.hopa.contracts.wire.Observation

/**
 * The seam between the loop and a graph.
 *
 * Null means "this graph has nothing to say here", never "do nothing": the
 * caller keeps the model path exactly as it is.
 */
interface GraphStepSource {
    fun next(observation: Observation, goal: String): GraphStep?

    /** The app a goal would start in, for the status line before a turn runs. */
    fun target(goal: String): GraphTarget?
}

/** Where a goal would go, for the chat status line. */
data class GraphTarget(
    val app: String,
    val name: String,
)

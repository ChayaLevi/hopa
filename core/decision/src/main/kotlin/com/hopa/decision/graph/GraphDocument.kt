package com.hopa.decision.graph

import kotlinx.serialization.Serializable

/**
 * The hand-written graph document: a few apps we know well enough to write down.
 *
 * It is data, not code: a new app is one object, and a wrong edge is a one-line
 * change. The matcher never guesses: a screen that does not match a node, or an
 * edge whose control is not on screen, returns null and the model takes over.
 */
@Serializable
data class GraphDocument(
    val schemaVersion: Int = 1,
    val graphs: List<AppGraph> = emptyList(),
)

@Serializable
data class AppGraph(
    val app: String,
    val name: String,
    val nodes: List<GraphNode> = emptyList(),
    val edges: List<GraphEdge> = emptyList(),
)

@Serializable
data class GraphNode(
    val id: String,
    /** Any of these visible strings identifies the screen; empty is never a match. */
    val texts: List<String> = emptyList(),
)

@Serializable
data class GraphEdge(
    /** A node id, or `start` for "not in this app yet". */
    val from: String,
    val to: String? = null,
    /** Words in the goal that make this edge relevant. */
    val goal: List<String> = emptyList(),
    val action: GraphAction,
    /** The answer when this edge finishes the goal. */
    val answer: String = "",
    val done: Boolean = false,
)

@Serializable
data class GraphAction(
    /** launch | tap | back | scroll */
    val kind: String,
    val app: String = "",
    /** Text or description of the control to tap; any of them may match. */
    val texts: List<String> = emptyList(),
    val direction: String = "down",
)

/** One step the graph is willing to take from the screen it was shown. */
data class GraphStep(
    val graph: String,
    val node: String,
    val action: com.hopa.contracts.wire.Action,
    val answer: String,
    val done: Boolean,
)

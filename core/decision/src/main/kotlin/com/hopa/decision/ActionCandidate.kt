package com.hopa.decision

import com.hopa.contracts.wire.Action

/**
 * One way forward, as a graph or a planner would offer it: an edge from the
 * current screen, with how many times it already led nowhere.
 *
 * With a single proposed action the loop passes one candidate; with a
 * navigation graph it passes every outgoing edge, and a layer picks one.
 */
data class ActionCandidate(
    val id: String,
    val action: Action,
    /** Times this edge was tried in this turn and produced no usable change. */
    val failures: Int = 0,
) {
    init {
        require(id.isNotBlank()) { "a candidate needs an id" }
        require(failures >= 0) { "failures cannot be negative: $failures" }
    }

    val actionClass: ActionClass get() = ActionClass.of(action.kindOrNull())

    /** Human-readable line for a trace: the kind and its target. */
    fun describe(): String {
        val target = action.app ?: action.ref ?: action.text ?: action.url ?: ""
        return if (target.isBlank()) action.kind else "${action.kind} $target"
    }
}

package com.hopa.agent.plan

/**
 * The numbered plan the person approves before anything is tapped.
 *
 * A port, so a playbook can answer from its own steps without a model, and a
 * test can answer without a provider.
 */
fun interface PlanSource {
    /** Short numbered steps for the goal, or an empty list when none came back. */
    suspend fun plan(goal: String): List<String>
}

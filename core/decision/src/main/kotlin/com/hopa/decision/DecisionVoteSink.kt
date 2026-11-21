package com.hopa.decision

/**
 * Where votes go. The app writes them into the run log; the JVM harness keeps
 * them in memory. A sink must never throw into the loop: a logging failure is
 * not a reason to stop a turn, so implementations catch their own errors.
 */
fun interface DecisionVoteSink {
    suspend fun record(context: DecisionContext, run: DecisionRun)
}

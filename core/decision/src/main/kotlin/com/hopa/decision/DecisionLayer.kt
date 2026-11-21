package com.hopa.decision

/**
 * The seam between the loop and whatever decides.
 *
 * Implementations: the rules layer, LAIX, an intent classifier, a model acting
 * as a judge. None of them may leak a vendor type through this interface: the
 * loop, the registry and the run log know only [LayerDecision].
 */
interface DecisionLayer {
    val metadata: LayerMetadata

    suspend fun decide(context: DecisionContext): LayerDecision
}

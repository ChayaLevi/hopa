package com.hopa.agent.graph

import com.hopa.contracts.ObservationHasher
import com.hopa.contracts.wire.Observation

/**
 * Which screen a node is. The hash comes from `ObservationHasher`, the same
 * digest a run record carries, so a graph learned from records matches the live
 * screen without a second notion of identity.
 */
data class ScreenKey(
    val app: String,
    val hash: String,
) {
    companion object {
        fun of(app: String, observation: Observation): ScreenKey =
            ScreenKey(app, ObservationHasher.hashOf(observation))

        fun fromHash(app: String, hash: String): ScreenKey = ScreenKey(app, hash)
    }
}

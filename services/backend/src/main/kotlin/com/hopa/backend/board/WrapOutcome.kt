package com.hopa.backend.board

import com.hopa.backend.protocol.GatewayError

/** What a wrap request did. */
sealed interface WrapOutcome {
    /**
     * Stored at [keyEpoch]. [bootstrap] when this created the scope's key;
     * [lostEpoch] is then the earlier epoch whose ops no device can read any more.
     */
    data class Stored(val keyEpoch: Int, val bootstrap: Boolean, val lostEpoch: Int?) : WrapOutcome

    data class Refused(val error: GatewayError) : WrapOutcome
}

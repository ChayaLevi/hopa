package com.hopa.backend.board.client

/** The answer to "my wrap of the scope key". */
sealed interface ScopeKeyAnswer {
    data class Held(
        val keyEpoch: Int,
        val wrappedKey: ByteArray,
        val wrapperDeviceId: String?,
        val wrapperPublicKey: ByteArray?,
    ) :
        ScopeKeyAnswer {
        override fun equals(other: Any?): Boolean = other is Held && other.keyEpoch == keyEpoch &&
            other.wrappedKey.contentEquals(wrappedKey)

        override fun hashCode(): Int = keyEpoch
    }

    /**
     * No wrap here. With no key in the household, [bootstrapEpoch] is the epoch to create; [lostEpoch],
     * when set, is an earlier epoch whose `parents` ops no device can read any more (say so).
     */
    data class Missing(val currentEpoch: Int?, val bootstrapEpoch: Int?, val lostEpoch: Int?) : ScopeKeyAnswer

    data class Refused(val status: Int, val code: String?) : ScopeKeyAnswer
}

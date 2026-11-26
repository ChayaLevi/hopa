package com.hopa.backend.board.client

/** What unwrapping a received scope-key wrap gave. */
sealed interface Unwrapped {
    class Key(val key: ByteArray) : Unwrapped

    /**
     * The wrap came from a public key this device never verified. The server could have swapped in
     * its own key and wrapped a key it knows, so the device refuses it until the person compares
     * [fingerprint] with the wrapping device's screen.
     */
    data class Unverified(val wrapperPublicKey: ByteArray, val fingerprint: String) : Unwrapped {
        override fun equals(other: Any?): Boolean = other is Unverified && other.fingerprint == fingerprint &&
            other.wrapperPublicKey.contentEquals(wrapperPublicKey)

        override fun hashCode(): Int = fingerprint.hashCode()
    }
}

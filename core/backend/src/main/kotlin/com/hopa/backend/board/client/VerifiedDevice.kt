package com.hopa.backend.board.client

/** A device public key this device verified itself (D16): it admitted that device, or the person
 * compared [KeyFingerprint]s on both screens. */
data class VerifiedDevice(
    val deviceId: String,
    val publicKey: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is VerifiedDevice && other.deviceId == deviceId && other.publicKey.contentEquals(publicKey)

    override fun hashCode(): Int = 31 * deviceId.hashCode() + publicKey.contentHashCode()
}

package com.hopa.backend.board.client

import com.hopa.backend.crypto.KeyWrap
import java.security.MessageDigest

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

/**
 * The parents key on one device (ADR-0033 §3, D3, D16). It is wrapped with ADR-0026's [KeyWrap] under
 * the HKDF label [INFO], so a scope-key wrap can never be unwrapped as a household key or the other
 * way round. A device wraps the key only for a public key it verified itself, and accepts a wrap only
 * from one: the server never chooses whose key is trusted.
 */
class ScopeKeys(
    private val ownPrivateKey: ByteArray,
    verified: Collection<VerifiedDevice>,
) {
    private val verified = verified.toList()

    /** A fresh parents key, for the bootstrap of the first epoch. */
    fun newKey(): ByteArray = KeyWrap.generateContentKey()

    /** The wrap of [key] for [deviceId], or null when this device never verified that device's key. */
    fun wrapFor(deviceId: String, key: ByteArray): ByteArray? {
        val target = verified.firstOrNull { it.deviceId == deviceId } ?: return null
        return KeyWrap.wrapForDevice(key, target.publicKey, ownPrivateKey, INFO)
    }

    /** Opens a wrap this device received; refuses one from an unverified wrapper (D16). */
    fun unwrap(wrapped: ByteArray, wrapperPublicKey: ByteArray): Unwrapped {
        if (verified.none { it.publicKey.contentEquals(wrapperPublicKey) }) {
            return Unwrapped.Unverified(wrapperPublicKey, KeyFingerprint.of(wrapperPublicKey))
        }
        return Unwrapped.Key(KeyWrap.unwrapByDevice(wrapped, wrapperPublicKey, ownPrivateKey, INFO))
    }

    companion object {
        val INFO: ByteArray = "hopa.scope.key.wrap.v1".toByteArray(Charsets.UTF_8)
    }
}

/**
 * A short fingerprint of a device public key that two people can compare on two screens: the first
 * 16 bytes of SHA-256 over `"hopa.device.fingerprint.v1" || key`, as eight groups of four hex digits.
 */
object KeyFingerprint {
    private val LABEL = "hopa.device.fingerprint.v1".toByteArray(Charsets.US_ASCII)
    private const val BYTES = 16
    private const val GROUP = 4

    fun of(publicKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(LABEL + publicKey).copyOf(BYTES)
        return digest.joinToString("") { "%02x".format(it) }.chunked(GROUP).joinToString(" ")
    }
}

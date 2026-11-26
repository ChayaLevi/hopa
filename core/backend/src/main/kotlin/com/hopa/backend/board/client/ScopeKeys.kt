package com.hopa.backend.board.client

import com.hopa.backend.crypto.KeyWrap

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

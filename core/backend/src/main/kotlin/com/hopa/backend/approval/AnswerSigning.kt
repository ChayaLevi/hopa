package com.hopa.backend.approval

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Ed25519 for approval answers (ADR-0028), on Bouncy Castle so Android API
 * 29-32 needs no JCA provider. A device's signing key is its 32-byte seed;
 * it never leaves the device.
 */
object AnswerSigning {
    const val SEED_BYTES = 32
    const val KEY_BYTES = 32
    const val SIGNATURE_BYTES = 64

    fun newSeed(): ByteArray = Ed25519PrivateKeyParameters(SecureRandom()).encoded

    fun publicKey(seed: ByteArray): ByteArray = private(seed).generatePublicKey().encoded

    fun sign(seed: ByteArray, message: ByteArray): ByteArray = Ed25519Signer().run {
        init(true, private(seed))
        update(message, 0, message.size)
        generateSignature()
    }

    /** False for a wrong signature, a wrong key, or bytes of the wrong size; never throws. */
    fun verify(key: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
        if (key.size != KEY_BYTES || signature.size != SIGNATURE_BYTES) return false
        return runCatching {
            Ed25519Signer().run {
                init(false, Ed25519PublicKeyParameters(key, 0))
                update(message, 0, message.size)
                verifySignature(signature)
            }
        }.getOrDefault(false)
    }

    /**
     * What a person compares on both screens: the first 8 bytes of
     * SHA-256(key) as four groups of four hex digits, e.g. `3f2a 91c0 7be4 d815`.
     */
    fun fingerprint(key: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(key)
        .take(FINGERPRINT_BYTES)
        .joinToString("") { "%02x".format(it) }
        .chunked(GROUP)
        .joinToString(" ")

    private fun private(seed: ByteArray): Ed25519PrivateKeyParameters {
        require(seed.size == SEED_BYTES) { "an Ed25519 seed is $SEED_BYTES bytes" }
        return Ed25519PrivateKeyParameters(seed, 0)
    }

    private const val FINGERPRINT_BYTES = 8
    private const val GROUP = 4
}

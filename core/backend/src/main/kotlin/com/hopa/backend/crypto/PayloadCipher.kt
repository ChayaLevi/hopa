package com.hopa.backend.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The encrypted task payload of R29: AES-256-GCM under the household content
 * key, empty AAD, a fresh 12-byte IV and a 128-bit tag. The blob is
 * `IV(12) || ciphertext || tag(16)`.
 *
 * [open] throws [javax.crypto.AEADBadTagException] for a tampered blob or a
 * wrong key. Malformed key or blob sizes throw [IllegalArgumentException]
 * before any decryption is attempted.
 */
object PayloadCipher {
    private const val KEY_BYTES = 32
    private const val IV_BYTES = 12
    private const val TAG_BYTES = 16
    private const val TAG_BITS = TAG_BYTES * 8
    private const val MIN_BLOB_BYTES = IV_BYTES + TAG_BYTES

    private val random = SecureRandom()

    fun seal(contentKey: ByteArray, plaintext: ByteArray): ByteArray {
        require(contentKey.size == KEY_BYTES) { "content key must be $KEY_BYTES bytes" }
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(contentKey, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plaintext)
    }

    fun open(contentKey: ByteArray, sealed: ByteArray): ByteArray {
        require(contentKey.size == KEY_BYTES) { "content key must be $KEY_BYTES bytes" }
        require(sealed.size >= MIN_BLOB_BYTES) {
            "sealed payload must be at least $MIN_BLOB_BYTES bytes"
        }
        val iv = sealed.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(contentKey, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }
}

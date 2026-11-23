package com.hopa.backend.crypto

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The household content-key wrap of ADR-0026, with the byte layout pinned by
 * ruling R8a.
 *
 * **Device wrap.** The wrapping device computes a static-static X25519 shared
 * secret between its own private key and the recipient's public key. Keys are
 * exchanged as:
 * - `peerPublicKey`: the raw 32-byte RFC 7748 u-coordinate octets, exactly
 *   the bytes `X25519Keys.publicKeyBytes` returns for a device public key;
 * - `ownPrivateKey`: the PKCS#8 `PrivateKey.encoded` bytes, as clients store
 *   them (the recipient's matching public key is recovered from them).
 *
 * The key-encryption key is
 * `HKDF-SHA256(ikm = ECDH shared secret, salt = SHA-256(ASCII "hopa.v1.keywrap" || raw recipient public key), info = UTF-8 "hopa.content.key.wrap", 32 bytes)`.
 * The content key is sealed with AES-256-GCM under that key: 12 random IV
 * bytes, a 128-bit tag, empty AAD. The blob is
 * `IV(12) || ciphertext(32) || tag(16)` = 60 bytes.
 *
 * **Passphrase wrap.** The UTF-8 bytes of the passphrase (no normalization;
 * callers normalize first) are stretched with Argon2id (m = 65536 KiB, t = 3,
 * p = 4, 16 random salt bytes, 32-byte output) and the raw output is the
 * AES-256-GCM key, with the same IV, tag and empty AAD. The blob is
 * `salt(16) || IV(12) || ciphertext(32) || tag(16)` = 76 bytes.
 *
 * Unwrapping throws [javax.crypto.AEADBadTagException] for a tampered blob, a
 * wrong device key or a wrong passphrase, so callers can treat all three as
 * one authentication failure. Malformed sizes and key encodings throw
 * [IllegalArgumentException] before any decryption is attempted.
 */
object KeyWrap {
    private const val CONTENT_KEY_BYTES = 32
    private const val X25519_BYTES = 32
    private const val IV_BYTES = 12
    private const val TAG_BYTES = 16
    private const val TAG_BITS = TAG_BYTES * 8
    private const val SALT_BYTES = 16
    private const val WRAPPED_BYTES = IV_BYTES + CONTENT_KEY_BYTES + TAG_BYTES
    private const val PASSPHRASE_WRAPPED_BYTES = SALT_BYTES + WRAPPED_BYTES

    private const val ARGON2_MEMORY_KIB = 65536
    private const val ARGON2_ITERATIONS = 3
    private const val ARGON2_PARALLELISM = 4

    private val SALT_LABEL = "hopa.v1.keywrap".toByteArray(Charsets.US_ASCII)
    private val INFO = "hopa.content.key.wrap".toByteArray(Charsets.UTF_8)
    private val random = SecureRandom()

    /** A fresh 32-byte content key from [SecureRandom]. */
    fun generateContentKey(): ByteArray = ByteArray(CONTENT_KEY_BYTES).also(random::nextBytes)

    /**
     * Wraps [contentKey] for the device holding the private half of
     * [peerPublicKey]. [ownPrivateKey] is the wrapping device's PKCS#8 key.
     */
    fun wrapForDevice(contentKey: ByteArray, peerPublicKey: ByteArray, ownPrivateKey: ByteArray): ByteArray {
        require(contentKey.size == CONTENT_KEY_BYTES) { "content key must be $CONTENT_KEY_BYTES bytes" }
        require(peerPublicKey.size == X25519_BYTES) { "peer public key must be $X25519_BYTES bytes" }
        require(ownPrivateKey.isNotEmpty()) { "private key must not be empty" }

        val kek = deviceKek(peerPublicKey, ownPrivateKey, recipientPublicKey = peerPublicKey)
        return seal(kek, contentKey)
    }

    /**
     * Unwraps a device blob with the recipient's PKCS#8 [ownPrivateKey].
     * [peerPublicKey] is the wrapping (sending) device's raw public key.
     */
    fun unwrapByDevice(wrapped: ByteArray, peerPublicKey: ByteArray, ownPrivateKey: ByteArray): ByteArray {
        require(wrapped.size == WRAPPED_BYTES) { "device wrap must be $WRAPPED_BYTES bytes" }
        require(peerPublicKey.size == X25519_BYTES) { "peer public key must be $X25519_BYTES bytes" }
        require(ownPrivateKey.isNotEmpty()) { "private key must not be empty" }

        // The HKDF salt names the recipient, so recover its public key from the
        // stored PKCS#8 private half rather than making callers pass it twice.
        val recipientPublicKey = publicKeyOf(ownPrivateKey)
        val kek = deviceKek(peerPublicKey, ownPrivateKey, recipientPublicKey)
        val contentKey = open(kek, wrapped, WRAPPED_BYTES, offset = IV_BYTES)
        require(contentKey.size == CONTENT_KEY_BYTES) {
            "device wrap does not carry a $CONTENT_KEY_BYTES-byte content key"
        }
        return contentKey
    }

    /** Wraps [contentKey] under a key derived from [passphrase]. */
    fun wrapWithPassphrase(contentKey: ByteArray, passphrase: CharArray): ByteArray {
        require(contentKey.size == CONTENT_KEY_BYTES) { "content key must be $CONTENT_KEY_BYTES bytes" }

        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val key = Argon2.deriveKey(
            password = passphrase,
            salt = salt,
            memoryKiB = ARGON2_MEMORY_KIB,
            iterations = ARGON2_ITERATIONS,
            parallelism = ARGON2_PARALLELISM,
            outputBytes = CONTENT_KEY_BYTES,
        )
        return salt + seal(key, contentKey)
    }

    /** Unwraps a [wrapWithPassphrase] blob; a wrong passphrase fails authentication. */
    fun unwrapWithPassphrase(wrapped: ByteArray, passphrase: CharArray): ByteArray {
        require(wrapped.size == PASSPHRASE_WRAPPED_BYTES) {
            "passphrase wrap must be $PASSPHRASE_WRAPPED_BYTES bytes"
        }

        val salt = wrapped.copyOfRange(0, SALT_BYTES)
        val key = Argon2.deriveKey(
            password = passphrase,
            salt = salt,
            memoryKiB = ARGON2_MEMORY_KIB,
            iterations = ARGON2_ITERATIONS,
            parallelism = ARGON2_PARALLELISM,
            outputBytes = CONTENT_KEY_BYTES,
        )
        val contentKey = open(
            key = key,
            blob = wrapped,
            end = PASSPHRASE_WRAPPED_BYTES,
            offset = SALT_BYTES + IV_BYTES,
            ivOffset = SALT_BYTES,
        )
        require(contentKey.size == CONTENT_KEY_BYTES) {
            "passphrase wrap does not carry a $CONTENT_KEY_BYTES-byte content key"
        }
        return contentKey
    }

    private fun deviceKek(
        peerPublicKey: ByteArray,
        ownPrivateKey: ByteArray,
        recipientPublicKey: ByteArray,
    ): ByteArray {
        val sharedSecret = sharedSecret(peerPublicKey, ownPrivateKey)
        val salt = MessageDigest.getInstance("SHA-256")
            .digest(SALT_LABEL + recipientPublicKey)
        return hkdfSha256(sharedSecret, salt, INFO, CONTENT_KEY_BYTES)
    }

    private fun sharedSecret(peerPublicKey: ByteArray, ownPrivateKey: ByteArray): ByteArray = try {
        val privateKey = privateKeyOf(ownPrivateKey)
        val publicKey = X25519PublicKeyParameters(peerPublicKey, 0)
        val agreement = X25519Agreement().apply { init(privateKey) }
        val secret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(publicKey, secret, 0)
        secret
    } catch (e: Exception) {
        throw IllegalArgumentException("not an X25519 key pair", e)
    }

    private fun privateKeyOf(ownPrivateKey: ByteArray): X25519PrivateKeyParameters {
        val parameters = try {
            PrivateKeyFactory.createKey(ownPrivateKey)
        } catch (e: Exception) {
            throw IllegalArgumentException("not a PKCS#8 X25519 private key", e)
        }
        return parameters as? X25519PrivateKeyParameters
            ?: throw IllegalArgumentException("not a PKCS#8 X25519 private key")
    }

    /** The raw RFC 7748 octets of the public key that belongs to [ownPrivateKey]. */
    private fun publicKeyOf(ownPrivateKey: ByteArray): ByteArray {
        return privateKeyOf(ownPrivateKey).generatePublicKey().encoded
    }

    private fun seal(key: ByteArray, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return iv + cipher.doFinal(plaintext)
    }

    /**
     * Decrypts `iv || ciphertext || tag`. The caller supplies the slice so both
     * blob layouts share the AES-GCM rules.
     */
    private fun open(key: ByteArray, blob: ByteArray, end: Int, offset: Int, ivOffset: Int = 0): ByteArray {
        val iv = blob.copyOfRange(ivOffset, ivOffset + IV_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(blob, offset, end - offset)
    }

    /** RFC 5869 HKDF with HMAC-SHA256; expand emits whole 32-byte blocks. */
    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, outputBytes: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val pseudoRandomKey = mac.doFinal(ikm)

        mac.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
        val output = ByteArray(outputBytes)
        var previous = ByteArray(0)
        var written = 0
        var counter = 1
        while (written < outputBytes) {
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            val take = minOf(previous.size, outputBytes - written)
            previous.copyInto(output, written, 0, take)
            written += take
            counter++
        }
        return output
    }
}

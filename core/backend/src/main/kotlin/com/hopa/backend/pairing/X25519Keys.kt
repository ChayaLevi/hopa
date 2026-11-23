package com.hopa.backend.pairing

import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.interfaces.XECPublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * X25519 in the two shapes the backend contract names: the raw 32-byte RFC 7748
 * u-coordinate octets for `publicKey`, and PKCS#8 for the stored private half.
 *
 * The octets are the same on both paths: RFC 8410 puts the RFC 7748 u-coordinate
 * directly in the SubjectPublicKeyInfo bit string, so the [XECPublicKey] branch
 * reverses the JDK's big-endian `u` back to those octets and the opaque X.509
 * fallback already has them.
 *
 * [generatePrivateKey] and [publicKeyBytes] (the PKCS#8 overload) use Bouncy
 * Castle only, so callers on Android API 29-32 can avoid the JDK/Conscrypt XDH
 * provider that arrived in API 33. [generate] and [privateKeyBase64] remain for
 * the existing JCA callers.
 */
object X25519Keys {
    fun generate(): KeyPair = KeyPairGenerator.getInstance("X25519").generateKeyPair()

    /** A fresh PKCS#8 X25519 private key, without the JCA XDH provider. */
    fun generatePrivateKey(): ByteArray =
        PrivateKeyInfoFactory.createPrivateKeyInfo(X25519PrivateKeyParameters(SecureRandom())).encoded

    /** The public half of a PKCS#8 private key, as exactly 32 RFC 7748 octets. */
    fun publicKeyBytes(privateKeyPkcs8: ByteArray): ByteArray =
        privateKeyOf(privateKeyPkcs8).generatePublicKey().encoded

    /** The u-coordinate as exactly 32 RFC 7748 octets, however BigInteger padded it. */
    fun publicKeyBytes(publicKey: PublicKey): ByteArray {
        (publicKey as? XECPublicKey)?.let { xec ->
            val encoded = xec.u.toByteArray()
            val start = maxOf(0, encoded.size - KEY_BYTES)
            val bigEndian = ByteArray(KEY_BYTES)
            encoded.copyInto(bigEndian, destinationOffset = KEY_BYTES - (encoded.size - start), startIndex = start)
            return bigEndian.reversedArray()
        }
        // Android's provider hands back an opaque X.509 key, not an XECPublicKey.
        val encoded = publicKey.encoded
        if (encoded != null &&
            encoded.size == SPKI_HEADER.size + KEY_BYTES &&
            encoded.copyOfRange(0, SPKI_HEADER.size).contentEquals(SPKI_HEADER)
        ) {
            return encoded.copyOfRange(SPKI_HEADER.size, encoded.size)
        }
        throw IllegalArgumentException("not an X25519 public key")
    }

    fun privateKeyBase64(privateKey: PrivateKey): String =
        Base64.getEncoder().encodeToString(PKCS8EncodedKeySpec(privateKey.encoded).encoded)

    private fun privateKeyOf(pkcs8: ByteArray): X25519PrivateKeyParameters {
        val parameters = try {
            PrivateKeyFactory.createKey(pkcs8)
        } catch (e: Exception) {
            throw IllegalArgumentException("not a PKCS#8 X25519 private key", e)
        }
        return parameters as? X25519PrivateKeyParameters
            ?: throw IllegalArgumentException("not a PKCS#8 X25519 private key")
    }

    private const val KEY_BYTES = 32

    /** DER SubjectPublicKeyInfo header for X25519 (OID 1.3.101.110), BIT STRING of 32 bytes. */
    private val SPKI_HEADER = byteArrayOf(
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00,
    )
}

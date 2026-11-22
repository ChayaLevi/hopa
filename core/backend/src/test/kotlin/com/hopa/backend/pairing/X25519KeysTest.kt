package com.hopa.backend.pairing

import org.bouncycastle.crypto.agreement.X25519Agreement
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.spec.NamedParameterSpec
import java.util.HexFormat

class X25519KeysTest {
    @Test
    fun `a jdk public key yields the same octets as bouncycastle`() {
        val pair = KeyPairGenerator.getInstance("XDH")
            .apply { initialize(NamedParameterSpec.X25519) }
            .generateKeyPair()
        val privateKey = PrivateKeyFactory.createKey(pair.`private`.encoded) as X25519PrivateKeyParameters

        assertArrayEquals(privateKey.generatePublicKey().encoded, X25519Keys.publicKeyBytes(pair.public))
    }

    @Test
    fun `the x509 fallback carries the same octets as the jdk path`() {
        val pair = KeyPairGenerator.getInstance("XDH")
            .apply { initialize(NamedParameterSpec.X25519) }
            .generateKeyPair()
        val octets = X25519Keys.publicKeyBytes(pair.public)

        assertArrayEquals(octets, X25519Keys.publicKeyBytes(FakePublicKey(SPKI_PREFIX + octets)))
    }

    @Test
    fun `a generated pkcs8 private key exposes its public octets`() {
        val first = X25519Keys.generatePrivateKey()
        val second = X25519Keys.generatePrivateKey()
        val publicKey = X25519Keys.publicKeyBytes(first)
        val parameters = PrivateKeyFactory.createKey(first) as X25519PrivateKeyParameters

        assertEquals(32, publicKey.size)
        assertFalse(first.contentEquals(second))
        assertArrayEquals(parameters.generatePublicKey().encoded, publicKey)
    }

    @Test
    fun `the rfc 7748 vector derives the published public keys and shared secret`() {
        val alicePrivate = pkcs8(ALICE_PRIVATE)
        val bobPrivate = pkcs8(BOB_PRIVATE)

        assertArrayEquals(octets(ALICE_PUBLIC), X25519Keys.publicKeyBytes(alicePrivate))
        assertArrayEquals(octets(BOB_PUBLIC), X25519Keys.publicKeyBytes(bobPrivate))

        val agreement = X25519Agreement().apply {
            init(PrivateKeyFactory.createKey(alicePrivate) as X25519PrivateKeyParameters)
        }
        val secret = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(X25519PublicKeyParameters(octets(BOB_PUBLIC), 0), secret, 0)

        assertArrayEquals(octets(SHARED), secret)
    }

    @Test
    fun `a malformed private key is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            X25519Keys.publicKeyBytes(ByteArray(16))
        }
    }

    private fun pkcs8(privateScalarHex: String): ByteArray = PrivateKeyInfoFactory.createPrivateKeyInfo(
        X25519PrivateKeyParameters(octets(privateScalarHex), 0),
    ).encoded

    private fun octets(hex: String): ByteArray = HexFormat.of().parseHex(hex)

    private class FakePublicKey(private val encoded: ByteArray) : PublicKey {
        override fun getAlgorithm(): String = "X25519"

        override fun getFormat(): String = "X.509"

        override fun getEncoded(): ByteArray = encoded
    }

    private companion object {
        /** DER SubjectPublicKeyInfo header for X25519 (OID 1.3.101.110). */
        val SPKI_PREFIX = byteArrayOf(
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00,
        )

        // RFC 7748 §6.1.
        const val ALICE_PRIVATE = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"
        const val ALICE_PUBLIC = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"
        const val BOB_PRIVATE = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb"
        const val BOB_PUBLIC = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f"
        const val SHARED = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742"
    }
}

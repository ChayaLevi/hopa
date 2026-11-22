package com.hopa.backend.crypto

import com.hopa.backend.pairing.X25519Keys
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.util.PrivateKeyInfoFactory
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.NamedParameterSpec
import java.util.HexFormat
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class KeyWrapTest {
    private val random = SecureRandom()

    private fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("XDH")
        .apply { initialize(NamedParameterSpec.X25519) }
        .generateKeyPair()

    private fun rawPublic(keyPair: KeyPair): ByteArray = X25519Keys.publicKeyBytes(keyPair.public)

    private fun contentKey(): ByteArray = KeyWrap.generateContentKey()

    /** A BC key as the client stores it: PKCS#8 private bytes and RFC 7748 public octets. */
    private fun bcKey(): Pair<ByteArray, ByteArray> {
        val privateKey = X25519PrivateKeyParameters(random)
        return PrivateKeyInfoFactory.createPrivateKeyInfo(privateKey).encoded to
            privateKey.generatePublicKey().encoded
    }

    private fun pkcs8(privateScalarHex: String): ByteArray = PrivateKeyInfoFactory.createPrivateKeyInfo(
        X25519PrivateKeyParameters(HexFormat.of().parseHex(privateScalarHex), 0),
    ).encoded

    private fun octets(hex: String): ByteArray = HexFormat.of().parseHex(hex)

    @Test
    fun `a device wrap round-trips through the recipient private key`() {
        val sender = newKeyPair()
        val recipient = newKeyPair()
        val key = contentKey()

        val wrapped = KeyWrap.wrapForDevice(key, rawPublic(recipient), sender.private.encoded)

        assertEquals(60, wrapped.size)
        assertArrayEquals(key, KeyWrap.unwrapByDevice(wrapped, rawPublic(sender), recipient.private.encoded))
    }

    @Test
    fun `the wrap uses the agreed salt, hkdf, iv and tag layout`() {
        val sender = newKeyPair()
        val recipient = newKeyPair()
        val key = contentKey()
        val wrapped = KeyWrap.wrapForDevice(key, rawPublic(recipient), sender.private.encoded)

        val sharedSecret = KeyAgreement.getInstance("X25519").run {
            init(recipient.private)
            doPhase(sender.public, true)
            generateSecret()
        }
        val salt = MessageDigest.getInstance("SHA-256")
            .digest("hopa.v1.keywrap".toByteArray(Charsets.US_ASCII) + rawPublic(recipient))
        val kek = hkdf32(sharedSecret, salt, "hopa.content.key.wrap".toByteArray(Charsets.UTF_8))

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"), GCMParameterSpec(128, wrapped, 0, 12))

        assertArrayEquals(key, cipher.doFinal(wrapped, 12, 60 - 12))
    }

    /** A single-block HKDF-SHA256 expand, written independently of [KeyWrap]. */
    private fun hkdf32(ikm: ByteArray, salt: ByteArray, info: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val pseudoRandomKey = mac.doFinal(ikm)
        mac.init(SecretKeySpec(pseudoRandomKey, "HmacSHA256"))
        mac.update(info)
        mac.update(1)
        return mac.doFinal()
    }

    @Test
    fun `a flipped wrap byte is an authentication failure`() {
        val sender = newKeyPair()
        val recipient = newKeyPair()
        val key = contentKey()
        val wrapped = KeyWrap.wrapForDevice(key, rawPublic(recipient), sender.private.encoded)
        wrapped[20] = (wrapped[20].toInt() xor 1).toByte()

        assertThrows(AEADBadTagException::class.java) {
            KeyWrap.unwrapByDevice(wrapped, rawPublic(sender), recipient.private.encoded)
        }
    }

    @Test
    fun `a wrap for one device does not open for another`() {
        val sender = newKeyPair()
        val recipientA = newKeyPair()
        val recipientB = newKeyPair()
        val key = contentKey()
        val wrapped = KeyWrap.wrapForDevice(key, rawPublic(recipientA), sender.private.encoded)

        assertThrows(AEADBadTagException::class.java) {
            KeyWrap.unwrapByDevice(wrapped, rawPublic(sender), recipientB.private.encoded)
        }
    }

    @Test
    fun `two wraps of one key differ`() {
        val sender = newKeyPair()
        val recipient = newKeyPair()
        val key = contentKey()

        val first = KeyWrap.wrapForDevice(key, rawPublic(recipient), sender.private.encoded)
        val second = KeyWrap.wrapForDevice(key, rawPublic(recipient), sender.private.encoded)

        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `a passphrase wrap round-trips`() {
        val key = contentKey()

        val wrapped = KeyWrap.wrapWithPassphrase(key, "correct horse battery staple".toCharArray())

        assertEquals(76, wrapped.size)
        assertArrayEquals(
            key,
            KeyWrap.unwrapWithPassphrase(wrapped, "correct horse battery staple".toCharArray()),
        )
    }

    @Test
    fun `a wrong passphrase is an authentication failure`() {
        val wrapped = KeyWrap.wrapWithPassphrase(contentKey(), "correct horse".toCharArray())

        assertThrows(AEADBadTagException::class.java) {
            KeyWrap.unwrapWithPassphrase(wrapped, "wrong horse".toCharArray())
        }
    }

    @Test
    fun `two passphrase wraps differ in salt and iv`() {
        val key = contentKey()

        val first = KeyWrap.wrapWithPassphrase(key, "recovery code".toCharArray())
        val second = KeyWrap.wrapWithPassphrase(key, "recovery code".toCharArray())

        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `the passphrase primitive does not normalize`() {
        val key = contentKey()
        val composed = "h\u00e9roe".toCharArray()
        val decomposed = "he\u0301roe".toCharArray()
        val wrapped = KeyWrap.wrapWithPassphrase(key, composed)

        assertThrows(AEADBadTagException::class.java) {
            KeyWrap.unwrapWithPassphrase(wrapped, decomposed)
        }
    }

    @Test
    fun `content keys are 32 random bytes`() {
        val first = contentKey()
        val second = contentKey()

        assertEquals(32, first.size)
        assertFalse(first.contentEquals(second))
    }

    @Test
    fun `wrong-size inputs are refused`() {
        val sender = newKeyPair()
        val recipient = newKeyPair()
        val publicKey = rawPublic(recipient)
        val privateKey = sender.private.encoded

        assertThrows(IllegalArgumentException::class.java) {
            KeyWrap.wrapForDevice(ByteArray(31), publicKey, privateKey)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KeyWrap.wrapForDevice(contentKey(), ByteArray(31), privateKey)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KeyWrap.unwrapByDevice(ByteArray(59), publicKey, privateKey)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KeyWrap.unwrapWithPassphrase(ByteArray(75), "passphrase".toCharArray())
        }
    }

    @Test
    fun `a fixed-octet device wrap round-trips`() {
        val (senderPrivate, senderPublic) = bcKey()
        val (recipientPrivate, recipientPublic) = bcKey()
        val key = contentKey()

        val wrapped = KeyWrap.wrapForDevice(key, recipientPublic, senderPrivate)

        assertEquals(60, wrapped.size)
        assertArrayEquals(key, KeyWrap.unwrapByDevice(wrapped, senderPublic, recipientPrivate))
    }

    @Test
    fun `the rfc 7748 vector wraps and unwraps`() {
        val key = contentKey()
        val alicePublic = octets(ALICE_PUBLIC)
        val bobPublic = octets(BOB_PUBLIC)

        val wrapped = KeyWrap.wrapForDevice(key, bobPublic, pkcs8(ALICE_PRIVATE))

        assertArrayEquals(key, KeyWrap.unwrapByDevice(wrapped, alicePublic, pkcs8(BOB_PRIVATE)))
    }

    @Test
    fun `a wrap does not open with the wrong sender key`() {
        val (senderPrivate, _) = bcKey()
        val (recipientPrivate, recipientPublic) = bcKey()
        val (_, otherPublic) = bcKey()
        val wrapped = KeyWrap.wrapForDevice(contentKey(), recipientPublic, senderPrivate)

        assertThrows(AEADBadTagException::class.java) {
            KeyWrap.unwrapByDevice(wrapped, otherPublic, recipientPrivate)
        }
    }

    @Test
    fun `a malformed private key is an illegal argument`() {
        val (senderPrivate, senderPublic) = bcKey()
        val (_, recipientPublic) = bcKey()
        val wrapped = KeyWrap.wrapForDevice(contentKey(), recipientPublic, senderPrivate)
        val malformed = ByteArray(16) { it.toByte() }

        assertThrows(IllegalArgumentException::class.java) {
            KeyWrap.wrapForDevice(contentKey(), recipientPublic, malformed)
        }
        assertThrows(IllegalArgumentException::class.java) {
            KeyWrap.unwrapByDevice(wrapped, senderPublic, malformed)
        }
    }

    private companion object {
        // RFC 7748 §6.1.
        const val ALICE_PRIVATE = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a"
        const val ALICE_PUBLIC = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"
        const val BOB_PRIVATE = "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb"
        const val BOB_PUBLIC = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f"
    }
}

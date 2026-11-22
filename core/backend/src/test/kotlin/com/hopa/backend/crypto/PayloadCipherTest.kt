package com.hopa.backend.crypto

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import javax.crypto.AEADBadTagException

class PayloadCipherTest {
    private val key = ByteArray(32) { it.toByte() }
    private val plaintext = """{"goal":"open settings"}""".toByteArray(Charsets.UTF_8)

    @Test
    fun `a sealed payload opens back to the plaintext`() {
        val sealed = PayloadCipher.seal(key, plaintext)

        assertEquals(12 + plaintext.size + 16, sealed.size)
        assertArrayEquals(plaintext, PayloadCipher.open(key, sealed))
    }

    @Test
    fun `the iv is fresh for every seal`() {
        val first = PayloadCipher.seal(key, plaintext)
        val second = PayloadCipher.seal(key, plaintext)

        assertFalse(first.contentEquals(second))
        assertArrayEquals(plaintext, PayloadCipher.open(key, first))
        assertArrayEquals(plaintext, PayloadCipher.open(key, second))
    }

    @Test
    fun `a tampered blob fails authentication`() {
        val sealed = PayloadCipher.seal(key, plaintext)
        sealed[sealed.size - 1] = (sealed[sealed.size - 1].toInt() xor 0x01).toByte()

        assertThrows(AEADBadTagException::class.java) {
            PayloadCipher.open(key, sealed)
        }
    }

    @Test
    fun `a wrong key fails authentication`() {
        val sealed = PayloadCipher.seal(key, plaintext)
        val wrong = ByteArray(32) { (it + 1).toByte() }

        assertThrows(AEADBadTagException::class.java) {
            PayloadCipher.open(wrong, sealed)
        }
    }

    @Test
    fun `bad key and blob sizes are rejected before decryption`() {
        assertThrows(IllegalArgumentException::class.java) {
            PayloadCipher.seal(ByteArray(16), plaintext)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PayloadCipher.open(ByteArray(31), PayloadCipher.seal(key, plaintext))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PayloadCipher.open(key, ByteArray(27))
        }
    }
}

package com.hopa.backend.board

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The board op cipher of ADR-0033 D13, as a test stand-in: AES-256-GCM under
 * the scope key, a fresh 12-byte IV, a 128-bit tag and AAD that binds the op
 * to its place (`"hopa.board.v1" || household || scope || opId || keyEpoch`).
 *
 * The exact AAD byte layout is pinned by the client core in phase C; here the
 * fields are UTF-8 joined by a zero byte and the epoch is 4 bytes big-endian.
 * The server never runs this: it only stores what devices sealed.
 */
object BoardTestCipher {
    private val random = SecureRandom()

    fun aad(householdId: String, scope: String, opId: String, keyEpoch: Int): ByteArray {
        val text = listOf("hopa.board.v1", householdId, scope, opId)
            .joinToString("\u0000", postfix = "\u0000")
            .toByteArray(Charsets.UTF_8)
        return text + ByteBuffer.allocate(Int.SIZE_BYTES).putInt(keyEpoch).array()
    }

    fun seal(key: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(aad)
        return iv + cipher.doFinal(plaintext)
    }

    /** Throws [javax.crypto.AEADBadTagException] for a wrong key, a tampered blob or another place. */
    fun open(key: ByteArray, aad: ByteArray, sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, sealed.copyOfRange(0, 12)))
        cipher.updateAAD(aad)
        return cipher.doFinal(sealed, 12, sealed.size - 12)
    }
}

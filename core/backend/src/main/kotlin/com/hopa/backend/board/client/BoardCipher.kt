package com.hopa.backend.board.client

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The board op cipher (ADR-0033 D13): AES-256-GCM under the scope's key (the household key for
 * `family`, the parents key for `parents`), a fresh 12-byte IV, a 128-bit tag, and AAD that binds the
 * ciphertext to its place, so it cannot be replayed under another op, scope, epoch or household, nor
 * read as a task body under the same household key (whose AAD is empty).
 *
 * The AAD byte layout, pinned here and by `spec/fixtures/family/board-cipher.json`:
 *
 * ```
 * "hopa.board.v1"                       13 bytes, ASCII, no length
 * len(household) || household           u32 big-endian byte length, UTF-8
 * len(scope)     || scope
 * len(opId)      || opId
 * keyEpoch                              u32 big-endian
 * ```
 *
 * Length prefixes make the layout unambiguous for any field content. The blob is
 * `IV(12) || ciphertext || tag(16)`, as `PayloadCipher`'s. [open] throws
 * [javax.crypto.AEADBadTagException] for a wrong key, a tampered blob or another place.
 */
object BoardCipher {
    private const val KEY_BYTES = 32
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    private val LABEL = "hopa.board.v1".toByteArray(Charsets.US_ASCII)
    private val random = SecureRandom()

    /** Where an op lives. The server attests none of it in the ciphertext; the AAD binds it. */
    data class Place(
        val householdId: String,
        val scope: String,
        val opId: String,
        val keyEpoch: Int,
    )

    fun aad(place: Place): ByteArray {
        require(place.keyEpoch >= 0) { "key epoch must not be negative" }
        val out = ByteArrayOutputStream()
        out.write(LABEL)
        for (field in listOf(place.householdId, place.scope, place.opId)) {
            val bytes = field.toByteArray(Charsets.UTF_8)
            out.write(u32(bytes.size))
            out.write(bytes)
        }
        out.write(u32(place.keyEpoch))
        return out.toByteArray()
    }

    fun seal(key: ByteArray, place: Place, plaintext: ByteArray): ByteArray =
        sealWithIv(key, place, plaintext, ByteArray(IV_BYTES).also(random::nextBytes))

    /** For the test vectors only: a fixed IV must never be reused with the same key. */
    internal fun sealWithIv(key: ByteArray, place: Place, plaintext: ByteArray, iv: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "board key must be $KEY_BYTES bytes" }
        require(iv.size == IV_BYTES) { "iv must be $IV_BYTES bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(aad(place))
        return iv + cipher.doFinal(plaintext)
    }

    fun open(key: ByteArray, place: Place, sealed: ByteArray): ByteArray {
        require(key.size == KEY_BYTES) { "board key must be $KEY_BYTES bytes" }
        require(sealed.size >= IV_BYTES + TAG_BYTES) { "sealed op must be at least ${IV_BYTES + TAG_BYTES} bytes" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BITS, sealed.copyOfRange(0, IV_BYTES)),
        )
        cipher.updateAAD(aad(place))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    private fun u32(value: Int): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()
}

package com.hopa.backend.board.client

import java.security.MessageDigest

/**
 * A short fingerprint of a device public key that two people can compare on two screens: the first
 * 16 bytes of SHA-256 over `"hopa.device.fingerprint.v1" || key`, as eight groups of four hex digits.
 */
object KeyFingerprint {
    private val LABEL = "hopa.device.fingerprint.v1".toByteArray(Charsets.US_ASCII)
    private const val BYTES = 16
    private const val GROUP = 4

    fun of(publicKey: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(LABEL + publicKey).copyOf(BYTES)
        return digest.joinToString("") { "%02x".format(it) }.chunked(GROUP).joinToString(" ")
    }
}

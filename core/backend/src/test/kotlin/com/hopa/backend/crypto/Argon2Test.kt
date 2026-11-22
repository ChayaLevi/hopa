package com.hopa.backend.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.SecureRandom
import java.util.Base64

class Argon2Test {
    private val random = SecureRandom()

    private fun salt(): ByteArray = ByteArray(16).also(random::nextBytes)

    private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    /** An encoded hash produced with arbitrary cost parameters, bypassing [Argon2]. */
    private fun encode(password: String, salt: ByteArray, memoryKiB: Int, iterations: Int, parallelism: Int): String {
        val generator = Argon2BytesGenerator().apply {
            init(
                Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                    .withSalt(salt)
                    .withMemoryAsKB(memoryKiB)
                    .withIterations(iterations)
                    .withParallelism(parallelism)
                    .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                    .build(),
            )
        }
        val hash = ByteArray(32)
        generator.generateBytes(password.toByteArray(Charsets.UTF_8), hash)
        val costs = "$memoryKiB,$iterations,$parallelism"
        return "argon2id\$$costs\$${base64Url(salt)}\$${base64Url(hash)}"
    }

    @Test
    fun `a password verifies against its hash`() {
        val encoded = Argon2.hashPassword("correct horse battery staple".toCharArray(), salt())

        assertTrue(Argon2.verifyPassword("correct horse battery staple".toCharArray(), encoded))
    }

    @Test
    fun `a wrong password does not verify`() {
        val encoded = Argon2.hashPassword("correct horse".toCharArray(), salt())

        assertFalse(Argon2.verifyPassword("wrong horse".toCharArray(), encoded))
    }

    @Test
    fun `different salts produce different hashes`() {
        val first = Argon2.hashPassword("same password".toCharArray(), salt())
        val second = Argon2.hashPassword("same password".toCharArray(), salt())

        assertNotEquals(first, second)
    }

    @Test
    fun `the encoded form is argon2id with the agreed parameters`() {
        val encoded = Argon2.hashPassword("correct horse".toCharArray(), salt())

        assertTrue(Regex("^argon2id\\\$65536,3,4\\\$[A-Za-z0-9_-]+\\\$[A-Za-z0-9_-]+$").matches(encoded))
        assertFalse(encoded.contains("="))
    }

    @Test
    fun `malformed encoded hashes return false`() {
        val malformed = listOf(
            "",
            "argon2id",
            "argon2id\$65536,3,4\$abcdef",
            "argon2id\$65536,3\$abcdef\$abcdef",
            "argon2id\$65536,3,4,5\$abcdef\$abcdef",
            "argon2i\$65536,3,4\$abcdef\$abcdef",
            "argon2id\$65536,3,4\$!!\$abcdef",
            "argon2id\$65536,3,4\$abcdef\$!!",
            "argon2id\$0,3,4\$abcdef\$abcdef",
            "argon2id\$65536,3,4\$\$abcdef",
        )

        malformed.forEach { encoded ->
            assertFalse(Argon2.verifyPassword("correct horse".toCharArray(), encoded), encoded)
        }
    }

    @Test
    fun `verification uses the parameters embedded in the encoded hash`() {
        val encoded = encode("correct horse", salt(), memoryKiB = 32768, iterations = 2, parallelism = 2)

        assertTrue(Argon2.verifyPassword("correct horse".toCharArray(), encoded))
        assertFalse(Argon2.verifyPassword("wrong horse".toCharArray(), encoded))
    }

    @Test
    fun `hashing requires a 16-byte salt`() {
        assertThrows(IllegalArgumentException::class.java) {
            Argon2.hashPassword("correct horse".toCharArray(), ByteArray(15))
        }
        assertThrows(IllegalArgumentException::class.java) {
            Argon2.hashPassword("correct horse".toCharArray(), ByteArray(17))
        }
    }

    @Test
    fun `the password bytes are utf-8`() {
        val salt = salt()
        val encoded = Argon2.hashPassword("héroe".toCharArray(), salt)

        assertTrue(Argon2.verifyPassword("héroe".toCharArray(), encoded))
        assertFalse(Argon2.verifyPassword("he\u0301roe".toCharArray(), encoded))
    }
}

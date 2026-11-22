package com.hopa.backend.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.util.Base64

/**
 * Argon2id password hashing and the recovery-code key derivation of ADR-0025.
 *
 * The encoded form is
 * `argon2id$<m>,<t>,<p>$<salt>$<hash>`, where the salt and hash are unpadded
 * base64url (RFC 4648 §5) and `m` is memory in KiB. [hashPassword] emits
 * `m = 65536`, `t = 3`, `p = 4` and a 32-byte hash. [verifyPassword] reads the
 * cost parameters back from the string, so a hash written with other costs
 * still verifies.
 *
 * The passphrase bytes are the UTF-8 encoding of the chars; the primitive does
 * no Unicode normalization, so callers normalize before hashing or wrapping.
 */
object Argon2 {
    private const val HASH_BYTES = 32
    private const val SALT_BYTES = 16
    private const val MEMORY_KIB = 65536
    private const val ITERATIONS = 3
    private const val PARALLELISM = 4

    // Bounds on the cost parameters read from an encoded string: a corrupt or
    // hostile hash must fail verification, not force an unbounded allocation.
    private const val MAX_MEMORY_KIB = 262144
    private const val MIN_MEMORY_KIB = 8
    private const val MAX_ITERATIONS = 64
    private const val MAX_PARALLELISM = 64
    private const val MIN_SALT_BYTES = 8
    private const val MAX_SALT_BYTES = 64
    private const val MIN_HASH_BYTES = 16
    private const val MAX_HASH_BYTES = 64

    private val base64Url = Base64.getUrlEncoder().withoutPadding()

    /**
     * Hashes [password] with a 16-byte caller-supplied [salt] and returns the
     * encoded `argon2id$65536,3,4$salt$hash` string.
     */
    fun hashPassword(password: CharArray, salt: ByteArray): String {
        require(salt.size == SALT_BYTES) { "salt must be $SALT_BYTES bytes" }
        val hash = deriveKey(
            password = password,
            salt = salt,
            memoryKiB = MEMORY_KIB,
            iterations = ITERATIONS,
            parallelism = PARALLELISM,
            outputBytes = HASH_BYTES,
        )
        val saltText = base64Url.encodeToString(salt)
        val hashText = base64Url.encodeToString(hash)
        return "argon2id\$$MEMORY_KIB,$ITERATIONS,$PARALLELISM\$$saltText\$$hashText"
    }

    /**
     * Recomputes the hash described by [encoded] and compares it in constant
     * time with [MessageDigest.isEqual]. Malformed or out-of-range input
     * returns `false`; this method never throws.
     */
    fun verifyPassword(password: CharArray, encoded: String): Boolean = try {
        verify(password, encoded)
    } catch (e: RuntimeException) {
        false
    }

    /**
     * The raw Argon2id output used as an AES key by [KeyWrap]. Callers pick the
     * cost parameters and output length; the recovery code uses 65536/3/4 and
     * 32 bytes.
     */
    internal fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        memoryKiB: Int,
        iterations: Int,
        parallelism: Int,
        outputBytes: Int,
    ): ByteArray {
        require(salt.isNotEmpty()) { "salt must not be empty" }
        require(outputBytes > 0) { "output length must be positive" }
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withMemoryAsKB(memoryKiB)
            .withIterations(iterations)
            .withParallelism(parallelism)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .build()
        val generator = Argon2BytesGenerator().apply { init(parameters) }
        val output = ByteArray(outputBytes)
        generator.generateBytes(String(password).toByteArray(Charsets.UTF_8), output)
        return output
    }

    private fun verify(password: CharArray, encoded: String): Boolean {
        val parts = encoded.split('$')
        if (parts.size != 4 || parts[0] != "argon2id") return false

        val costs = parts[1].split(',')
        if (costs.size != 3) return false
        val memoryKiB = costs[0].toInt()
        val iterations = costs[1].toInt()
        val parallelism = costs[2].toInt()
        if (memoryKiB !in MIN_MEMORY_KIB..MAX_MEMORY_KIB) return false
        if (iterations !in 1..MAX_ITERATIONS) return false
        if (parallelism !in 1..MAX_PARALLELISM) return false

        val salt = decode(parts[2]) ?: return false
        val expected = decode(parts[3]) ?: return false
        if (salt.size !in MIN_SALT_BYTES..MAX_SALT_BYTES) return false
        if (expected.size !in MIN_HASH_BYTES..MAX_HASH_BYTES) return false

        val actual = deriveKey(password, salt, memoryKiB, iterations, parallelism, expected.size)
        return MessageDigest.isEqual(actual, expected)
    }

    private fun decode(text: String): ByteArray? = try {
        Base64.getUrlDecoder().decode(text)
    } catch (e: IllegalArgumentException) {
        null
    }
}

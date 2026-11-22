package com.hopa.sidecar

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The admitted PC's identity and household key. The file holds tokens and key
 * material, so it is written owner-only where the filesystem supports it, and
 * [toString] never prints a token or a key.
 */
@Serializable
data class BackendSession(
    val deviceId: String,
    val householdId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String,
    val gatewayUrl: String,
    val contentKeyBase64: String,
    val privateKeyBase64: String,
) {
    override fun toString(): String =
        "BackendSession(deviceId=$deviceId, householdId=$householdId, " +
            "accessToken=<redacted>, refreshToken=<redacted>, expiresAt=$expiresAt, " +
            "gatewayUrl=$gatewayUrl, contentKeyBase64=<redacted>, privateKeyBase64=<redacted>)"
}

class BackendSessionStore(private val file: Path) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun load(): BackendSession? {
        if (!Files.exists(file)) return null
        return runCatching {
            json.decodeFromString(BackendSession.serializer(), Files.readString(file))
        }.getOrNull()
    }

    fun save(session: BackendSession) {
        file.parent?.let { Files.createDirectories(it) }
        Files.writeString(file, json.encodeToString(BackendSession.serializer(), session))
        ownerOnly(file)
    }

    /** POSIX permissions where available; Windows ACLs are inherited from the user profile. */
    private fun ownerOnly(path: Path) {
        runCatching {
            Files.getFileAttributeView(path, PosixFileAttributeView::class.java)
                ?.setPermissions(PosixFilePermissions.fromString("rw-------"))
        }
    }
}

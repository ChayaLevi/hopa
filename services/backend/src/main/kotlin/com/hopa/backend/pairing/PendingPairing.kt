package com.hopa.backend.pairing

import com.hopa.backend.ServerConfig
import com.hopa.backend.http.BodyTooLarge
import com.hopa.backend.http.RateLimiter
import com.hopa.backend.http.receiveBounded
import com.hopa.backend.http.respondError
import com.hopa.backend.store.Database
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.security.MessageDigest
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.Serializable

/** A new phone or PC asking to be admitted, before any household exists. */
@Serializable
data class PendingRequest(
    val platform: String,
    val name: String,
    val publicKey: String,
    val alg: String,
)

/** The one-time answer to a mint: the raw secret leaves the server exactly here. */
@Serializable
data class PendingMinted(
    val sessionId: String,
    val secret: String,
    val qr: String,
    val expiresAt: String,
)

/** What a poller may learn from a session it holds the secret for. */
@Serializable
data class PendingState(
    val sessionId: String,
    val state: String,
    val name: String,
    val platform: String,
    val publicKey: String,
    val fingerprint: String,
    val expiresAt: String,
)

@Serializable
data class PendingResolveRequest(val sessionSecret: String)

/**
 * Pending pairing sessions are minted by the device that wants in and polled by
 * it until an operator vouches and claims. The row is keyed only by the hash of
 * the secret in the QR, so the poller never needs a session id, a household or
 * an account: it presents the secret, the token GUC makes exactly that row
 * visible, and nothing else is stored in the clear.
 *
 * The two verbs have separate per-address budgets: minting is rare (5/min), while
 * polling is the PC/sidecar's liveness loop (30/min, enough for every 5 s over
 * the five-minute session).
 */
class PendingPairingService(
    private val db: Database,
    private val clock: () -> Long = System::currentTimeMillis,
    private val publicHost: String =
        System.getenv("HOPA_PUBLIC_HOST") ?: "hopa-backend.onrender.com",
    mintPerMinute: Int = 5,
    resolvePerMinute: Int = 30,
) {
    private val mintLimiter = RateLimiter(mintPerMinute, 60_000, clock)
    private val resolveLimiter = RateLimiter(resolvePerMinute, 60_000, clock)

    fun mint(request: PendingRequest, remoteAddress: String): PairingOutcome<PendingMinted> {
        if (!mintLimiter.allow(remoteAddress)) {
            return PairingOutcome.Refused("E_RATE", "too many pending pairings", retryable = true)
        }
        if (request.platform !in PLATFORMS) {
            return PairingOutcome.Refused("E_SCHEMA", "platform must be android or windows")
        }
        if (request.name.isBlank()) {
            return PairingOutcome.Refused("E_SCHEMA", "name must not be blank")
        }
        if (request.name.length > MAX_NAME) {
            return PairingOutcome.Refused("E_SCHEMA", "name is longer than $MAX_NAME characters")
        }
        if (request.alg != "x25519") {
            return PairingOutcome.Refused("E_SCHEMA", "alg must be x25519")
        }
        val publicKey = decodeKey(request.publicKey)
            ?: return PairingOutcome.Refused("E_SCHEMA", "publicKey is not base64")
        if (publicKey.size != 32) {
            return PairingOutcome.Refused("E_SCHEMA", "publicKey must be 32 bytes")
        }

        val sessionId = UUID.randomUUID().toString()
        val secret = Tokens.mint()
        val codeHash = Tokens.hash(secret)
        val hash = Tokens.hex(codeHash)
        val expiresAt = clock() + SESSION_MS

        db.token(hash) { connection ->
            connection.prepareStatement(
                "insert into pending_pairings(id, code_hash, platform, name, public_key, " +
                    "alg, expires_at) values (?::uuid, ?, ?, ?, ?, ?, ?)",
            ).use { statement ->
                statement.setString(1, sessionId)
                statement.setBytes(2, codeHash)
                statement.setString(3, request.platform)
                statement.setString(4, request.name)
                statement.setBytes(5, publicKey)
                statement.setString(6, request.alg)
                statement.setTimestamp(7, Timestamp.from(Instant.ofEpochMilli(expiresAt)))
                statement.executeUpdate()
            }
        }

        // The pending QR names only the secret and the gateway: it carries no
        // household, because none exists until an operator vouches.
        val qr = "hopa://pair?v=1&s=$secret&u=wss://$publicHost/v1/gateway"
        return PairingOutcome.Ok(
            PendingMinted(
                sessionId = sessionId,
                secret = secret,
                qr = qr,
                expiresAt = Instant.ofEpochMilli(expiresAt).toString(),
            ),
        )
    }

    fun resolve(secret: String, remoteAddress: String): PairingOutcome<PendingState> {
        if (!resolveLimiter.allow(remoteAddress)) {
            return PairingOutcome.Refused("E_RATE", "too many pending pairing polls", retryable = true)
        }
        val hash = Tokens.hex(Tokens.hash(secret))
        val row = db.token(hash) { connection ->
            connection.prepareStatement(
                "select id::text, platform, name, public_key, expires_at, " +
                    "vouched_at, claimed_at from pending_pairings " +
                    "where code_hash = decode(?, 'hex')",
            ).use { statement ->
                statement.setString(1, hash)
                statement.executeQuery().use { rows ->
                    if (!rows.next()) {
                        null
                    } else {
                        PendingRow(
                            id = rows.getString(1),
                            platform = rows.getString(2),
                            name = rows.getString(3),
                            publicKey = rows.getBytes(4),
                            expiresAt = rows.getTimestamp(5).toInstant().toEpochMilli(),
                            vouched = rows.getTimestamp(6) != null,
                            claimed = rows.getTimestamp(7) != null,
                        )
                    }
                }
            }
        } ?: return PairingOutcome.Refused("E_SCHEMA", "unknown pairing session")

        // claimed wins over expiry: an admission is final. Otherwise an expired
        // session says so even if an operator had vouched for it.
        val state = when {
            row.claimed -> "claimed"
            row.expiresAt <= clock() -> "expired"
            row.vouched -> "vouched"
            else -> "pending"
        }
        return PairingOutcome.Ok(
            PendingState(
                sessionId = row.id,
                state = state,
                name = row.name,
                platform = row.platform,
                publicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(row.publicKey),
                fingerprint = fingerprint(row.publicKey),
                expiresAt = Instant.ofEpochMilli(row.expiresAt).toString(),
            ),
        )
    }

    private fun decodeKey(value: String): ByteArray? =
        try {
            Base64.getDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            try {
                Base64.getUrlDecoder().decode(value)
            } catch (_: IllegalArgumentException) {
                null
            }
        }

    private fun fingerprint(publicKey: ByteArray): String =
        Tokens.hex(MessageDigest.getInstance("SHA-256").digest(publicKey)).take(8)

    private data class PendingRow(
        val id: String,
        val platform: String,
        val name: String,
        val publicKey: ByteArray,
        val expiresAt: Long,
        val vouched: Boolean,
        val claimed: Boolean,
    )

    private companion object {
        const val SESSION_MS = 5 * 60 * 1000L
        const val MAX_NAME = 100
        val PLATFORMS = setOf("android", "windows")
    }
}

fun Application.pendingPairingRoutes(config: ServerConfig) {
    val api = config.pendingPairings ?: return

    routing {
        post("/v1/pair/pending") {
            val request = try {
                call.receiveBounded(PendingRequest.serializer())
            } catch (_: BodyTooLarge) {
                call.respondError(
                    HttpStatusCode.PayloadTooLarge,
                    "E_SCHEMA",
                    "request body is larger than 64 KiB",
                )
                return@post
            } catch (_: Exception) {
                call.respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "invalid JSON body")
                return@post
            }
            when (val outcome = api.mint(request, remoteAddress(call))) {
                is PairingOutcome.Ok -> call.respond(HttpStatusCode.Created, outcome.value)
                is PairingOutcome.Refused -> call.refuse(outcome)
            }
        }

        post("/v1/pair/pending/resolve") {
            val request = try {
                call.receiveBounded(PendingResolveRequest.serializer())
            } catch (_: BodyTooLarge) {
                call.respondError(
                    HttpStatusCode.PayloadTooLarge,
                    "E_SCHEMA",
                    "request body is larger than 64 KiB",
                )
                return@post
            } catch (_: Exception) {
                call.respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "invalid JSON body")
                return@post
            }
            when (val outcome = api.resolve(request.sessionSecret, remoteAddress(call))) {
                is PairingOutcome.Ok -> call.respond(HttpStatusCode.OK, outcome.value)
                is PairingOutcome.Refused -> call.refuse(outcome)
            }
        }
    }
}

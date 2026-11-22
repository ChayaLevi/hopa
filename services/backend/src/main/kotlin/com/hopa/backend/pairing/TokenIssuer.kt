package com.hopa.backend.pairing

import com.hopa.backend.store.Database
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class Issued(val token: String, val expiresAt: String)

sealed interface RotateOutcome {
    data class Rotated(val access: Issued, val refresh: Issued) : RotateOutcome

    data object Refused : RotateOutcome
}

/**
 * Issues, rotates and verifies the two opaque tokens. Hashing is the only thing
 * stored; the plaintext exists in this process and on the device, nowhere else.
 */
class TokenIssuer(
    private val db: Database,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onRevoke: (deviceId: String, householdId: String) -> Unit = { _, _ -> },
) {
    fun issueAccess(deviceId: String, householdId: String, memberId: String = ""): Issued =
        db.tenant(householdId) { connection ->
            issueAccessOn(connection, deviceId, householdId)
        }

    /** For a caller that already holds a household transaction, so the insert commits with it. */
    internal fun issueAccessOn(connection: Connection, deviceId: String, householdId: String): Issued {
        val token = Tokens.mint()
        val expiresAt = clock() + ACCESS_MS
        connection.prepareStatement(
            "insert into access_tokens(id, device_id, household_id, token_hash, expires_at) " +
                "values (?::uuid, ?::uuid, ?::uuid, ?, ?)",
        ).use {
            it.setString(1, UUID.randomUUID().toString())
            it.setString(2, deviceId)
            it.setString(3, householdId)
            it.setBytes(4, Tokens.hash(token))
            it.setTimestamp(5, Timestamp.from(Instant.ofEpochMilli(expiresAt)))
            it.executeUpdate()
        }
        return Issued(token, Instant.ofEpochMilli(expiresAt).toString())
    }

    fun issueRefresh(
        deviceId: String,
        householdId: String,
        rotatedFrom: String? = null,
    ): Issued =
        db.tenant(householdId) { connection ->
            issueRefreshOn(connection, deviceId, householdId, rotatedFrom)
        }

    /** For a caller that already holds a household transaction, so the insert commits with it. */
    internal fun issueRefreshOn(
        connection: Connection,
        deviceId: String,
        householdId: String,
        rotatedFrom: String? = null,
    ): Issued {
        val token = Tokens.mint()
        val expiresAt = clock() + REFRESH_MS
        connection.prepareStatement(
            "insert into refresh_tokens(id, device_id, household_id, token_hash, expires_at, rotated_from) " +
                "values (?::uuid, ?::uuid, ?::uuid, ?, ?, ?::uuid)",
        ).use {
            it.setString(1, UUID.randomUUID().toString())
            it.setString(2, deviceId)
            it.setString(3, householdId)
            it.setBytes(4, Tokens.hash(token))
            it.setTimestamp(5, Timestamp.from(Instant.ofEpochMilli(expiresAt)))
            it.setString(6, rotatedFrom)
            it.executeUpdate()
        }
        return Issued(token, Instant.ofEpochMilli(expiresAt).toString())
    }

    /**
     * A refresh token is single use. Presenting one that was already rotated is
     * treated as theft: the device's whole family dies, including its live access
     * token, and the caller has to pair again.
     */
    fun rotate(refreshToken: String): RotateOutcome {
        val hash = Tokens.hex(Tokens.hash(refreshToken))
        val row = db.token(hash) { connection ->
            connection.prepareStatement(
                "select id, device_id, household_id, expires_at, revoked_at from refresh_tokens " +
                    "where token_hash = decode(?, 'hex')",
            ).use {
                it.setString(1, hash)
                it.executeQuery().use { rows ->
                    if (!rows.next()) {
                        null
                    } else {
                        RefreshRow(
                            id = rows.getString(1),
                            deviceId = rows.getString(2),
                            householdId = rows.getString(3),
                            expiresAt = rows.getTimestamp(4).toInstant().toEpochMilli(),
                            revoked = rows.getTimestamp(5) != null,
                        )
                    }
                }
            }
        } ?: return RotateOutcome.Refused

        if (row.expiresAt <= clock()) return RotateOutcome.Refused

        if (row.revoked) {
            val rotated = db.tenant(row.householdId) { connection ->
                connection.prepareStatement(
                    "select 1 from refresh_tokens where rotated_from = ?::uuid limit 1",
                ).use {
                    it.setString(1, row.id)
                    it.executeQuery().use { rows -> rows.next() }
                }
            }
            if (rotated) {
                revokeFamily(row.deviceId, row.householdId)
                onRevoke(row.deviceId, row.householdId)
            }
            return RotateOutcome.Refused
        }

        // Rotation and issuance are one tenant transaction, and the device row
        // is locked for update. A concurrent revoke therefore either waits for
        // this turn (and then revokes the freshly issued family) or commits
        // first (and the revoked_at check refuses to issue anything). Issuing
        // outside this transaction would let a revoke slip into the gap and
        // leave a live refresh token behind.
        val attempt = db.tenant(row.householdId) { connection ->
            val (present, revokedAt) = connection.prepareStatement(
                "select revoked_at from devices where id = ?::uuid and household_id = ?::uuid for update",
            ).use {
                it.setString(1, row.deviceId)
                it.setString(2, row.householdId)
                it.executeQuery().use { rows ->
                    if (!rows.next()) false to null else true to rows.getTimestamp(1)
                }
            }
            when {
                !present -> RotateAttempt.NoDevice
                revokedAt != null -> RotateAttempt.Revoked
                else -> {
                    val won = connection.prepareStatement(
                        "update refresh_tokens set revoked_at = now() " +
                            "where id = ?::uuid and revoked_at is null",
                    ).use {
                        it.setString(1, row.id)
                        it.executeUpdate()
                    }
                    if (won != 1) {
                        RotateAttempt.Revoked
                    } else {
                        RotateAttempt.Done(
                            access = issueAccessOn(connection, row.deviceId, row.householdId),
                            refresh = issueRefreshOn(
                                connection,
                                row.deviceId,
                                row.householdId,
                                rotatedFrom = row.id,
                            ),
                        )
                    }
                }
            }
        }
        return when (attempt) {
            is RotateAttempt.Done -> RotateOutcome.Rotated(attempt.access, attempt.refresh)
            RotateAttempt.Revoked -> {
                revokeFamily(row.deviceId, row.householdId)
                onRevoke(row.deviceId, row.householdId)
                RotateOutcome.Refused
            }
            RotateAttempt.NoDevice -> {
                revokeFamily(row.deviceId, row.householdId)
                RotateOutcome.Refused
            }
        }
    }

    private fun revokeFamily(deviceId: String, householdId: String) {
        db.tenant(householdId) { connection ->
            connection.prepareStatement(
                "delete from access_tokens where device_id = ?::uuid",
            ).use {
                it.setString(1, deviceId)
                it.executeUpdate()
            }
            connection.prepareStatement(
                "update refresh_tokens set revoked_at = now() " +
                    "where device_id = ?::uuid and revoked_at is null",
            ).use {
                it.setString(1, deviceId)
                it.executeUpdate()
            }
        }
    }

    private data class RefreshRow(
        val id: String,
        val deviceId: String,
        val householdId: String,
        val expiresAt: Long,
        val revoked: Boolean,
    )

    private sealed interface RotateAttempt {
        data class Done(val access: Issued, val refresh: Issued) : RotateAttempt

        data object Revoked : RotateAttempt

        data object NoDevice : RotateAttempt
    }

    private companion object {
        const val ACCESS_MS = 15 * 60 * 1000L
        const val REFRESH_MS = 30L * 24 * 60 * 60 * 1000
    }
}

package com.hopa.backend.admin

import com.hopa.backend.pairing.Tokens
import com.hopa.backend.store.Database
import java.sql.Timestamp
import java.time.Instant

/** One authenticated console operator. */
data class AdminIdentity(val adminId: String, val email: String, val role: String)

/**
 * Resolves an admin bearer token. `admins` and `admin_sessions` are global
 * tables without row-level security, so the lookup runs in [Database.read]
 * (no tenant GUC) and the expiry is checked in the same statement: a token is
 * valid only while its session row is live.
 */
class AdminAuth(
    private val db: Database,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun adminFor(token: String): AdminIdentity? {
        val hash = Tokens.hex(Tokens.hash(token))
        return db.read { connection ->
            connection.prepareStatement(
                "select a.id, a.email, a.role " +
                    "from admin_sessions s join admins a on a.id = s.admin_id " +
                    "where s.token_hash = decode(?, 'hex') and s.expires_at > ?",
            ).use {
                it.setString(1, hash)
                it.setTimestamp(2, Timestamp.from(Instant.ofEpochMilli(clock())))
                it.executeQuery().use { rows ->
                    if (!rows.next()) {
                        null
                    } else {
                        AdminIdentity(
                            adminId = rows.getString(1),
                            email = rows.getString(2),
                            role = rows.getString(3),
                        )
                    }
                }
            }
        }
    }
}

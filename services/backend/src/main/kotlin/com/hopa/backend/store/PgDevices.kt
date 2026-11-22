package com.hopa.backend.store

import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.tasks.DeviceDirectory
import java.sql.Timestamp
import java.time.Instant

/** One device as the inventory lists it. */
data class DeviceRow(
    val id: String,
    val platform: String,
    val name: String,
    val lastSeenAt: String? = null,
    val revokedAt: String? = null,
)

/** Listing and revocation are SQL-backed; the gateway's [DeviceDirectory] stays read-only. */
interface DeviceInventory {
    fun list(householdId: String): List<DeviceRow>

    /** False when the device is not in this household; true also when already revoked. */
    fun revokeDevice(deviceId: String, householdId: String): Boolean
}

/** The paired-device directory, read and written behind row-level security. */
class PgDevices(private val db: Database) : DeviceDirectory, DeviceInventory {
    override fun lookup(deviceId: String, householdId: String): DeviceIdentity? =
        db.tenant(householdId) { connection ->
            connection.prepareStatement(
                "select id, household_id, member_id from devices " +
                    "where id = ?::uuid and revoked_at is null",
            ).use {
                it.setString(1, deviceId)
                it.executeQuery().use { rows ->
                    if (!rows.next()) {
                        null
                    } else {
                        DeviceIdentity(
                            deviceId = rows.getString(1),
                            householdId = rows.getString(2),
                            memberId = rows.getString(3),
                        )
                    }
                }
            }
        }

    override fun pushToken(deviceId: String, householdId: String): String? =
        db.tenant(householdId) { connection ->
            connection.prepareStatement(
                "select push_token from devices where id = ?::uuid and revoked_at is null",
            ).use {
                it.setString(1, deviceId)
                it.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            }
        }

    override fun touch(deviceId: String, householdId: String, pushToken: String?, at: Long) {
        db.tenant(householdId) { connection ->
            val sql = if (pushToken == null) {
                "update devices set last_seen_at = ? where id = ?::uuid"
            } else {
                "update devices set last_seen_at = ?, push_token = ? where id = ?::uuid"
            }
            connection.prepareStatement(sql).use {
                var index = 1
                it.setTimestamp(index++, Timestamp.from(Instant.ofEpochMilli(at)))
                if (pushToken != null) it.setString(index++, pushToken)
                it.setString(index, deviceId)
                it.executeUpdate()
            }
        }
    }

    override fun clearPushToken(deviceId: String, householdId: String) {
        db.tenant(householdId) { connection ->
            connection.prepareStatement(
                "update devices set push_token = null where id = ?::uuid",
            ).use {
                it.setString(1, deviceId)
                it.executeUpdate()
            }
        }
    }

    override fun list(householdId: String): List<DeviceRow> =
        db.tenant(householdId) { connection ->
            connection.prepareStatement(
                "select id, platform, name, last_seen_at, revoked_at from devices " +
                    "order by created_at, id",
            ).use {
                it.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(
                                DeviceRow(
                                    id = rows.getString(1),
                                    platform = rows.getString(2),
                                    name = rows.getString(3),
                                    lastSeenAt = rows.getTimestamp(4)?.toInstant()?.toString(),
                                    revokedAt = rows.getTimestamp(5)?.toInstant()?.toString(),
                                ),
                            )
                        }
                    }
                }
            }
        }

    /**
     * Every effect of a revoke commits together: the device row, its access
     * tokens, its refresh family and its household key wrap. The first read is
     * also the tenancy check; row-level security hides another household's id.
     */
    override fun revokeDevice(deviceId: String, householdId: String): Boolean =
        db.tenant(householdId) { connection ->
            val present = connection.prepareStatement(
                "select 1 from devices where id = ?::uuid",
            ).use {
                it.setString(1, deviceId)
                it.executeQuery().use { rows -> rows.next() }
            }
            if (!present) return@tenant false

            connection.prepareStatement(
                "update devices set revoked_at = now() " +
                    "where id = ?::uuid and revoked_at is null",
            ).use {
                it.setString(1, deviceId)
                it.executeUpdate()
            }
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
            connection.prepareStatement(
                "delete from household_key_wraps where device_id = ?::uuid",
            ).use {
                it.setString(1, deviceId)
                it.executeUpdate()
            }
            true
        }
}

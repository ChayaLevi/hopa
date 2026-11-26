package com.hopa.backend.board

import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.harness.newDevice
import com.hopa.backend.harness.newHousehold
import com.hopa.backend.harness.seedHousehold
import com.hopa.backend.harness.seedMember
import java.util.UUID

/** Seeds board households in Postgres through the harness. */
object BoardHouseholds {
    /** A new household with one device per entry of [roles], each of its own member and family role. */
    fun seed(roles: List<String?>): List<DeviceIdentity> {
        val db = PostgresHarness.appDatabase()
        val household = newHousehold()
        return roles.mapIndexed { index, role ->
            val device = newDevice()
            val member = UUID.randomUUID().toString()
            if (index == 0) {
                seedHousehold(db, household, device, member)
                db.tenant(household) { connection ->
                    connection.prepareStatement("update members set family_role = ? where id = ?::uuid").use {
                        it.setString(1, role)
                        it.setString(2, member)
                        it.executeUpdate()
                    }
                }
            } else {
                seedMember(db, household, member, device, role)
            }
            DeviceIdentity(device, household, member)
        }
    }

    /** Stores a device's raw X25519 public key, as a claim does. */
    fun setPublicKey(device: DeviceIdentity, publicKey: ByteArray) {
        PostgresHarness.appDatabase().tenant(device.householdId) { connection ->
            connection.prepareStatement("update devices set public_key = ?, alg = 'x25519' where id = ?::uuid").use {
                it.setBytes(1, publicKey)
                it.setString(2, device.deviceId)
                it.executeUpdate()
            }
        }
    }
}

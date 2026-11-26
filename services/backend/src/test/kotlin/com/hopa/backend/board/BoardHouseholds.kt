package com.hopa.backend.board

import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.harness.grantPermissions
import com.hopa.backend.harness.newDevice
import com.hopa.backend.harness.newHousehold
import com.hopa.backend.harness.seedHousehold
import com.hopa.backend.harness.seedMember
import com.hopa.backend.members.Permissions
import java.util.UUID

/** Seeds board households in Postgres through the harness. */
object BoardHouseholds {
    /** Shown to users as a parent: holds the private scope. */
    val PARENT: Set<String> = setOf(Permissions.BOARD_PRIVATE)

    /** A parent who also manages the group, as a household founder is. */
    val MANAGER: Set<String> = Permissions.FOUNDER

    /** Shown to users as a child: no permission. */
    val CHILD: Set<String> = emptySet()

    /** A new household with one device per entry of [members], each of its own member with those permissions. */
    fun seed(members: List<Set<String>>): List<DeviceIdentity> {
        val db = PostgresHarness.appDatabase()
        val household = newHousehold()
        return members.mapIndexed { index, permissions ->
            val device = newDevice()
            val member = UUID.randomUUID().toString()
            if (index == 0) {
                seedHousehold(db, household, device, member)
                grantPermissions(db, household, member, permissions)
            } else {
                seedMember(db, household, member, device, permissions)
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

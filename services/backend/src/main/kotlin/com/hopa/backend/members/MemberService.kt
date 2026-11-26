package com.hopa.backend.members

import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.gateway.DeviceRegistry
import com.hopa.backend.protocol.GatewayError
import java.util.UUID

/**
 * Members and permissions on top of [MemberStore]. The caller's household and
 * member always come from [DeviceIdentity]. A member who loses
 * [Permissions.BOARD_PRIVATE] has their live sockets closed after the commit,
 * so their devices start again from what they may read now; from then on the
 * server gives them no `parents` op and no new wrap.
 */
class MemberService(
    private val store: MemberStore,
    private val registry: DeviceRegistry,
) {
    fun list(caller: DeviceIdentity): List<GroupMember> = store.list(caller.householdId)

    fun change(caller: DeviceIdentity, memberId: String, permissions: List<String>): PermissionOutcome {
        if (!isUuid(memberId)) return refused("E_SCHEMA", "member id must be a UUID")
        if (permissions.any { it !in Permissions.ALL }) return refused("E_SCHEMA", "unknown permission")
        if (permissions.size != permissions.toSet().size) return refused("E_SCHEMA", "a permission is listed twice")
        val outcome = store.change(caller.householdId, PermissionChange(caller.memberId, memberId, permissions.toSet()))
        if (outcome is PermissionOutcome.Changed) {
            // After the commit, so a socket is never closed before the rights it
            // would reconnect to are gone.
            outcome.lostPrivate.forEach(registry::disconnect)
        }
        return outcome
    }

    private fun refused(code: String, message: String) = PermissionOutcome.Refused(GatewayError(code, message))

    private fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)
}

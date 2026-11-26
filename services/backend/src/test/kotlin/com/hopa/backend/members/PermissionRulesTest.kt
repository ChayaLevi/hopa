package com.hopa.backend.members

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** [PermissionRules] on hand-made snapshots (B5). */
class PermissionRulesTest {
    private val manage = setOf(Permissions.GROUP_MANAGE)
    private val all = Permissions.ALL

    private fun change(vararg permissions: String) = PermissionChange("caller", "target", permissions.toSet())

    @Test
    fun `a manager may change anyone in the group`() {
        assertNull(PermissionRules.decide(change(Permissions.BOARD_PRIVATE), PermissionSnapshot(manage, emptySet(), 1)))
        assertNull(PermissionRules.decide(change(), PermissionSnapshot(all, setOf(Permissions.BOARD_PRIVATE), 1)))
    }

    @Test
    fun `a member without group manage is refused, whatever the change`() {
        val refused = PermissionRules.decide(change(), PermissionSnapshot(setOf(Permissions.BOARD_PRIVATE), emptySet(), 1))
        assertEquals("E_SCOPE", refused?.code)
    }

    @Test
    fun `a member outside the group is not found`() {
        assertEquals("E_TENANT", PermissionRules.decide(change(), PermissionSnapshot(manage, null, 1))?.code)
    }

    @Test
    fun `the last manager stays a manager, one of two may step down`() {
        assertEquals("E_CONFLICT", PermissionRules.decide(change(Permissions.BOARD_PRIVATE), PermissionSnapshot(all, all, 1))?.code)
        assertNull(PermissionRules.decide(change(Permissions.BOARD_PRIVATE), PermissionSnapshot(all, all, 2)))
        assertNull(PermissionRules.decide(change(*all.toTypedArray()), PermissionSnapshot(all, all, 1)), "keeping it is fine")
    }
}

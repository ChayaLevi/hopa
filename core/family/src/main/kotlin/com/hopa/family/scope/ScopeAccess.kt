package com.hopa.family.scope

import com.hopa.family.model.Family
import com.hopa.family.model.FamilyRole
import com.hopa.family.model.Member
import com.hopa.family.model.Scope

/**
 * Who reads and writes which scope (D3, D11). The parents key is wrapped only for parent devices,
 * so this is what the cryptography already enforces, written down once for the client: screens,
 * the engine and the helpers in this package ask here instead of checking roles themselves.
 */
object ScopeAccess {
    /** The scopes a device of [member] holds: a parent both, anyone else `family` only. */
    fun heldBy(member: Member): Set<Scope> =
        if (member.role == FamilyRole.PARENT) setOf(Scope.FAMILY, Scope.PARENTS) else setOf(Scope.FAMILY)

    /** Whether [member] may write ops in [scope]: they need an account and must hold the scope. */
    fun mayWrite(member: Member, scope: Scope): Boolean = member.hasAccount && scope in heldBy(member)

    /** Members who could own an item in [scope]: everyone with an account, only parents in `parents`. */
    fun eligibleOwners(family: Family, scope: Scope): List<String> =
        family.members.filter { it.hasAccount && scope in heldBy(it) }.map { it.id }
}

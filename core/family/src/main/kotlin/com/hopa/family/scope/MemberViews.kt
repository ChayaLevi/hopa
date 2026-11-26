package com.hopa.family.scope

import com.hopa.family.model.BoardState
import com.hopa.family.model.Scope

/**
 * The view of the board for one member, the base for every screen, search and model context on a
 * device. A port of Homemind's `viewFor` (`server/homemind/visibility.ts`) under D11: the scope is
 * the visibility, and no item in one scope can name an item in the other, so there is nothing to
 * redact across scopes. What a teen must not see is simply not in the state their device holds.
 */
object MemberViews {
    /**
     * The view for [memberId], or null for someone who is not in the family. A parent on a device
     * that does not hold the parents key yet sees `family` only, as a teen does.
     */
    fun viewFor(state: BoardState, memberId: String): MemberView? {
        val member = state.family.members.firstOrNull { it.id == memberId } ?: return null
        val scopes = ScopeAccess.heldBy(member).filter { state.partition(it) != null }.toSet()
        val partitions = SCOPE_ORDER.filter { it in scopes }.associateWith { requireNotNull(state.partition(it)) }
        return MemberView(
            memberId = memberId,
            family = state.family,
            scopes = scopes,
            items = partitions.flatMap { (scope, partition) -> partition.items.map { ScopedItem(scope, it) } },
            activity = partitions
                .flatMap { (scope, partition) ->
                    partition.activity.map { ScopedActivity(scope, it.copy(undo = null)) }
                }.sortedWith(compareBy({ it.activity.at }, { it.scope.ordinal })),
        )
    }

    /** Where [itemId] lives on this device, or null. The same id may exist in both scopes (D11). */
    fun find(state: BoardState, scope: Scope, itemId: String): ScopedItem? =
        state.partition(scope)?.items?.firstOrNull { it.id == itemId }?.let { ScopedItem(scope, it) }

    private val SCOPE_ORDER = listOf(Scope.FAMILY, Scope.PARENTS)
}

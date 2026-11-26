package com.hopa.family.scope

import com.hopa.family.model.Family
import com.hopa.family.model.Scope

/**
 * What one member is shown: the items and the activity of the scopes they may read, merged.
 * Activity comes without its undo patches, which hold whole earlier versions of items and are only
 * for the reducer (as in Homemind's `viewFor`).
 */
data class MemberView(
    val memberId: String,
    val family: Family,
    val scopes: Set<Scope>,
    val items: List<ScopedItem>,
    val activity: List<ScopedActivity>,
)

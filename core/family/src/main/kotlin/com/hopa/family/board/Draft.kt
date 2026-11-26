package com.hopa.family.board

import com.hopa.family.model.Activity
import com.hopa.family.model.ActivityKind
import com.hopa.family.model.Item

/**
 * What an op will change, before versions and the activity are written. [next] maps item ids to
 * their new content in the order the op touched them; a null value removes the item.
 */
internal data class Draft(
    val next: LinkedHashMap<String, Item?>,
    val kind: ActivityKind,
    val itemId: String?,
    val what: String,
    val memberId: String? = null,
    val note: String? = null,
    val ref: String? = null,
    /** Undo marks the original activity instead of writing an undo patch. */
    val undoes: Activity? = null,
) {
    companion object {
        fun one(
            item: Item,
            kind: ActivityKind,
            what: String,
            memberId: String? = null,
            note: String? = null,
            ref: String? = null,
        ) = Draft(linkedMapOf(item.id to item), kind, item.id, what, memberId, note, ref)
    }
}

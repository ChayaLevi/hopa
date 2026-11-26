package com.hopa.family.shopping

import kotlinx.serialization.Serializable

/** Who bought an item, and when. */
@Serializable
data class Bought(val by: String, val at: String)

/** One thing to buy, at one store. */
@Serializable
data class ShoppingItem(
    val id: String,
    val storeId: String,
    val name: String,
    val nameZh: String? = null,
    /** How many (1 to 99). Absent: one. */
    val qty: Int? = null,
    val note: String? = null,
    val noteZh: String? = null,
    val addedBy: String,
    val addedAt: String,
    val bought: Bought? = null,
)

/** What a finished run took off the list, kept so an Undo of its task on the board brings it back. */
@Serializable
data class Settled(val cleared: List<ShoppingItem>)

/** Who took a store's shopping run, and its task on the board. */
@Serializable
data class Run(
    val memberId: String,
    val at: String,
    val itemId: String? = null,
    val settled: Settled? = null,
)

@Serializable
data class ShoppingStore(
    val id: String,
    val name: String,
    val nameZh: String? = null,
    /** Added by the family (not a default): only such a store can be removed, and only when empty. */
    val custom: Boolean? = null,
    val createdBy: String? = null,
    val run: Run? = null,
)

/** One entry of an Undo record: an item or a store as it was before and after the change. */
@Serializable
data class Change<T>(
    val id: String,
    val before: T? = null,
    val after: T? = null,
    /** Where a removed entry stood, so Undo puts it back in its place. */
    val index: Int? = null,
)

/** One list change, kept so its author (or a parent) can undo it. Its id is the op id. */
@Serializable
data class ShoppingUndo(
    val id: String,
    val at: String,
    val by: String,
    val items: List<Change<ShoppingItem>>,
    val stores: List<Change<ShoppingStore>>,
    val undone: String? = null,
)

/** The `shopping` namespace's state in a scope: the stores, what to buy, and the recent Undo records. */
@Serializable
data class ShoppingState(
    val stores: List<ShoppingStore>,
    val items: List<ShoppingItem>,
    val undo: List<ShoppingUndo>? = null,
)

/** What members see: the stores (a run following its task) and the items (a finished run's bought ones gone). */
data class ShoppingLists(
    val stores: List<ShoppingStore>,
    val items: List<ShoppingItem>,
)

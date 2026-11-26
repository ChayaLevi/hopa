package com.hopa.family.shopping

import com.hopa.family.model.Item
import com.hopa.family.model.ItemStatus
import com.hopa.family.model.Ownership
import com.hopa.family.model.Partition
import com.hopa.family.namespace.Namespaces
import com.hopa.family.shopping.ShoppingNamespace.Companion.json
import kotlinx.serialization.json.JsonObject

/**
 * The lists as the board makes them (Homemind's `settleShoppingRuns` and `shoppingOf`). A run lives
 * by its task on the board, which this capability reads but never writes:
 *  - its task done: the shopping there was done; the bought items leave the list (kept in the run as
 *    `settled`, so an Undo of the task on the board brings them back);
 *  - its task open again after that: the cleared items and the run come back;
 *  - its task reassigned: the run follows its new owner;
 *  - its task handed back, removed or gone: the run is over; the whole list stays.
 * [settle] is applied before every shopping op and to every view, so the result is the same as
 * Homemind applying it after every board change. Pure.
 */
object ShoppingView {
    val DEFAULT_STORES = listOf(
        ShoppingStore("supermarket", "Supermarket", "超市"),
        ShoppingStore("pharmacy", "Pharmacy", "药店"),
        ShoppingStore("mall", "Mall", "商场"),
        ShoppingStore("post-office", "Post office", "邮局"),
        ShoppingStore("hardware", "Hardware store", "五金店"),
        ShoppingStore("market", "Market", "菜市场"),
    )

    /** The stored lists, or the default stores with nothing on them. */
    fun stored(own: JsonObject?): ShoppingState =
        own?.let { json.decodeFromJsonElement(ShoppingState.serializer(), it) }
            ?: ShoppingState(DEFAULT_STORES, emptyList())

    /** What a scope's members see. */
    fun of(partition: Partition): ShoppingLists {
        val current =
            follow(settle(stored(partition.ns?.get(ShoppingNamespace.NAME)), partition.items), partition.items)
        return ShoppingLists(current.stores, current.items)
    }

    /** Homemind's `settleShoppingRuns`: the stored lists brought in line with the runs' tasks. */
    fun settle(state: ShoppingState, board: List<Item>): ShoppingState {
        var items = state.items
        val stores = state.stores.map { store ->
            val run = store.run
            if (run?.itemId == null) return@map store
            val task = board.firstOrNull { it.id == run.itemId }
            val owner = (task?.ownership as? Ownership.Assigned)?.ownerId
            when {
                task?.status == ItemStatus.DONE -> {
                    if (run.settled != null) return@map store
                    val cleared = items.filter { it.storeId == store.id && it.bought != null }
                    items = items.filterNot { it.storeId == store.id && it.bought != null }
                    store.copy(run = run.copy(settled = Settled(cleared)))
                }
                task != null && task.status == ItemStatus.OPEN && owner != null -> {
                    var next = store
                    if (run.settled != null) {
                        val have = items.map { it.id }.toSet()
                        items = items + run.settled.cleared.filter { it.id !in have }
                        next = next.copy(run = run.copy(settled = null))
                    }
                    if (owner != run.memberId) next = next.copy(run = next.run?.copy(memberId = owner))
                    next
                }
                else -> store.copy(run = null)
            }
        }
        return state.copy(stores = stores, items = items)
    }

    /** Homemind's `shoppingOf`: a run whose task is not open and assigned is over; else it follows the owner. */
    fun follow(state: ShoppingState, board: List<Item>): ShoppingState = state.copy(
        stores = state.stores.map { store ->
            val run = store.run
            if (run?.itemId == null) return@map store
            val task = board.firstOrNull { it.id == run.itemId }
            val owner = (task?.ownership as? Ownership.Assigned)?.ownerId
            when {
                task == null || task.status != ItemStatus.OPEN || owner == null -> store.copy(run = null)
                owner == run.memberId -> store
                else -> store.copy(run = run.copy(memberId = owner))
            }
        },
    )

    /** The board this capability reads: [Namespaces.BOARD]. */
    const val READS = Namespaces.BOARD
}

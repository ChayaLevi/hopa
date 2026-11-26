package com.hopa.family.shopping

import com.hopa.family.board.RejectCode
import com.hopa.family.namespace.NamespaceStep
import com.hopa.family.shopping.ShoppingNamespace.Companion.json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * Undo of one list change (Homemind's `withUndo` and `undoShopping`): what each item and store looked
 * like before and after, so the change can be put back by its author or a parent, once, and only while
 * every item and store it touched is still as it left them (else someone changed it since: conflict).
 */
internal object ShoppingUndoing {
    private fun <T> same(a: T?, b: T?, encode: (T) -> JsonElement): Boolean =
        (a?.let(encode) ?: JsonNull) == (b?.let(encode) ?: JsonNull)

    private fun item(i: ShoppingItem): JsonElement = json.encodeToJsonElement(ShoppingItem.serializer(), i)

    private fun store(s: ShoppingStore): JsonElement = json.encodeToJsonElement(ShoppingStore.serializer(), s)

    /** A store as an Undo record compares it: who is going is not part of a list change. */
    private fun plain(s: ShoppingStore?) = s?.copy(run = null)

    private fun <T> diff(
        before: List<T>,
        after: List<T>,
        idOf: (T) -> String,
        encode: (T) -> JsonElement,
    ): List<Change<T>> {
        val ids = (before.map(idOf) + after.map(idOf)).distinct()
        return ids.mapNotNull { id ->
            val b = before.firstOrNull { idOf(it) == id }
            val a = after.firstOrNull { idOf(it) == id }
            if (same(b, a, encode)) null else Change(id, b, a, if (b != null && a == null) before.indexOf(b) else null)
        }
    }

    fun withUndo(prev: ShoppingState, next: ShoppingState, id: String, by: String, at: String): ShoppingState {
        val record = ShoppingUndo(
            id = id,
            at = at,
            by = by,
            items = diff(prev.items, next.items, { it.id }, ::item),
            stores = diff(prev.stores.map { plain(it)!! }, next.stores.map { plain(it)!! }, { it.id }, ::store),
        )
        return next.copy(undo = (prev.undo.orEmpty() + record).takeLast(ShoppingNamespace.MAX_UNDO))
    }

    private fun <T> putBack(list: List<T>, entry: T, index: Int?): List<T> {
        val at = if (index == null) list.size else index.coerceIn(0, list.size)
        return list.take(at) + entry + list.drop(at)
    }

    @Suppress("ReturnCount", "CyclomaticComplexMethod", "LongParameterList")
    fun undo(
        raw: ShoppingState,
        undoId: String?,
        actor: String,
        isParent: Boolean,
        at: String,
        edit: ShoppingNamespace.Edit,
    ): NamespaceStep {
        val record = raw.undo.orEmpty().firstOrNull { it.id == undoId }
            ?: return ShoppingNamespace.reject(RejectCode.NOT_FOUND)
        if (record.undone != null) return edit.same()
        if (record.by != actor && !isParent) return ShoppingNamespace.reject(RejectCode.FORBIDDEN)
        val changed = ShoppingNamespace.reject(RejectCode.CONFLICT)
        for (entry in record.items) if (!same(
                raw.items.firstOrNull { it.id == entry.id },
                entry.after,
                ::item,
            )
        ) {
            return changed
        }
        for (entry in record.stores) if (!same(
                plain(
                    raw.stores.firstOrNull {
                        it.id == entry.id
                    },
                ),
                entry.after,
                ::store,
            )
        ) {
            return changed
        }

        var stores = raw.stores
        for (entry in record.stores) {
            val now = stores.firstOrNull { it.id == entry.id }
            val before = entry.before
            stores = when {
                before != null && now != null -> stores.map {
                    if (it.id == entry.id) before.copy(run = now.run) else it
                }
                before != null -> putBack(stores, before, entry.index)
                now?.run != null -> return changed
                else -> stores.filter { it.id != entry.id }
            }
        }
        var items = raw.items
        for (entry in record.items.sortedBy { it.index ?: Int.MAX_VALUE }) {
            val before = entry.before
            items = when {
                before != null && items.any { it.id == entry.id } -> items.map { if (it.id == entry.id) before else it }
                before != null -> putBack(items, before, entry.index)
                else -> items.filter { it.id != entry.id }
            }
        }
        val storeIds = stores.map { it.id }.toSet()
        if (items.any { it.storeId !in storeIds }) return changed
        val undo = raw.undo.orEmpty().map { if (it.id == record.id) it.copy(undone = at) else it }
        return edit.keep(raw.copy(stores = stores, items = items, undo = undo))
    }
}

package com.hopa.family.shopping

import com.hopa.family.board.RejectCode
import com.hopa.family.model.FamilyRole
import com.hopa.family.model.Item
import com.hopa.family.model.ItemStatus
import com.hopa.family.model.Ownership
import com.hopa.family.namespace.Namespace
import com.hopa.family.namespace.NamespaceContext
import com.hopa.family.namespace.NamespaceStep
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * The family's shopping lists, per store (Homemind's `shopping.ts`), as the `shopping` capability:
 * any member with an account adds, removes, marks and clears; one member at a time takes a store's
 * run; a list change can be undone by its author or a parent, once, while nothing it touched changed
 * since. Ids come in the op (D10); an Undo record's id is the op id of its change.
 *
 * A run's task is a board task: the device writes it as a board op next to `run.take`, and
 * finishing is the board `done` of that task. This capability reads the board ([ShoppingView]) and
 * never writes it. `spec/fixtures/family/ns-shopping.json` is the contract.
 */
class ShoppingNamespace : Namespace {
    override val name = NAME
    override val version = 1
    override val reads = setOf(ShoppingView.READS)

    override fun knows(kind: String, nsv: Int): Boolean = nsv == 1 && kind in KINDS

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    override fun apply(context: NamespaceContext, op: JsonObject): NamespaceStep {
        val kind = string(op["kind"]) ?: return NamespaceStep.Stopped
        if (kind !in KINDS) return NamespaceStep.Stopped
        val at = string(op["at"]) ?: return reject(RejectCode.INVALID)
        val opId = string(op["opId"]) ?: return reject(RejectCode.INVALID)
        val board = context.board?.items.orEmpty()
        val raw = ShoppingView.settle(ShoppingView.stored(context.own), board)
        val current = ShoppingView.follow(raw, board)
        val actor = context.actorId
        val change = Edit(raw, opId, at, actor)
        return when (kind) {
            "item.add" -> {
                val entry = entry(op) ?: return reject(RejectCode.INVALID)
                if (current.stores.none { it.id == entry.storeId }) return reject(RejectCode.NOT_FOUND)
                if (duplicate(current, entry.storeId, entry.name) != null) return reject(RejectCode.CONFLICT)
                if (raw.items.any { it.id == entry.id }) return reject(RejectCode.CONFLICT)
                if (current.items.size >= MAX_ITEMS) return reject(RejectCode.CONFLICT)
                change.record(raw.copy(items = raw.items + entry.item(actor, at)))
            }
            "item.addMany" -> addMany(op, raw, current, change)
            "item.remove" -> {
                val item = item(op, current) ?: return reject(RejectCode.NOT_FOUND)
                change.record(raw.copy(items = raw.items.filter { it.id != item.id }))
            }
            "item.buy" -> {
                val item = item(op, current) ?: return reject(RejectCode.NOT_FOUND)
                val bought = (op["bought"] as? JsonPrimitive)?.takeIf {
                    !it.isString
                }?.booleanOrNull ?: return reject(RejectCode.INVALID)
                if ((item.bought != null) == bought) return change.same()
                val next = if (bought) item.copy(bought = Bought(actor, at)) else item.copy(bought = null)
                change.record(raw.copy(items = raw.items.map { if (it.id == item.id) next else it }))
            }
            "item.bump", "item.unbump", "item.setQty" -> {
                val item = item(op, current) ?: return reject(RejectCode.NOT_FOUND)
                val have = item.qty ?: 1
                val next = when (kind) {
                    "item.bump" -> minOf(have + 1, MAX_QTY)
                    "item.unbump" -> maxOf(have - 1, 1)
                    else -> qty(op["qty"]) ?: return reject(RejectCode.INVALID)
                }
                if (next == have) return change.same()
                change.record(raw.copy(items = raw.items.map { if (it.id == item.id) it.copy(qty = next) else it }))
            }
            "list.clearBought" -> {
                val store = store(op, current) ?: return reject(RejectCode.NOT_FOUND)
                val left = raw.items.filterNot { it.storeId == store.id && it.bought != null }
                if (left.size == raw.items.size) return change.same()
                change.record(raw.copy(items = left))
            }
            "run.take" -> {
                val store = store(op, current) ?: return reject(RejectCode.NOT_FOUND)
                if (store.run?.memberId == actor) return change.same()
                if (store.run != null) return reject(RejectCode.CONFLICT)
                val taskId = string(op["taskId"]) ?: return reject(RejectCode.INVALID)
                if (!isRunTask(board.firstOrNull { it.id == taskId }, actor)) return reject(RejectCode.INVALID)
                change.keep(
                    raw.copy(
                        stores = raw.stores.map {
                            if (it.id == store.id) {
                                it.copy(
                                    run = Run(actor, at, taskId),
                                )
                            } else {
                                it
                            }
                        },
                    ),
                )
            }
            "run.release" -> {
                val store = store(op, current) ?: return reject(RejectCode.NOT_FOUND)
                val run = store.run ?: return change.same()
                if (run.memberId != actor) return reject(RejectCode.FORBIDDEN)
                change.keep(raw.copy(stores = raw.stores.map { if (it.id == store.id) it.copy(run = null) else it }))
            }
            "store.add" -> addStore(op, raw, current, change)
            "store.remove" -> {
                val store = store(op, current) ?: return reject(RejectCode.NOT_FOUND)
                if (store.custom != true) return reject(RejectCode.INVALID)
                if (current.items.any { it.storeId == store.id }) return reject(RejectCode.CONFLICT)
                if (store.run != null) return reject(RejectCode.CONFLICT)
                change.record(raw.copy(stores = raw.stores.filter { it.id != store.id }))
            }
            "undo" -> ShoppingUndoing.undo(raw, string(op["undoId"]), actor, isParent(context, actor), at, change)
            else -> NamespaceStep.Stopped
        }
    }

    private fun addMany(op: JsonObject, raw: ShoppingState, current: ShoppingState, change: Edit): NamespaceStep {
        val list = op["items"] as? JsonArray ?: return reject(RejectCode.INVALID)
        if (list.isEmpty() || list.size > MAX_CHAT_ITEMS) return reject(RejectCode.INVALID)
        var next = raw
        var seen = current
        var added = 0
        for (element in list) {
            val fields = element as? JsonObject ?: return reject(RejectCode.INVALID)
            val entry = entry(fields) ?: return reject(RejectCode.INVALID)
            if (next.stores.none { it.id == entry.storeId }) return reject(RejectCode.NOT_FOUND)
            // What is already waiting is not added again (the device showed it as a duplicate).
            if (duplicate(seen, entry.storeId, entry.name) != null) continue
            if (next.items.any { it.id == entry.id }) return reject(RejectCode.CONFLICT)
            if (next.items.size >= MAX_ITEMS) return reject(RejectCode.CONFLICT)
            val item = entry.item(change.actor, change.at)
            next = next.copy(items = next.items + item)
            seen = seen.copy(items = seen.items + item)
            added++
        }
        if (added == 0) return reject(RejectCode.CONFLICT)
        return change.record(next)
    }

    private fun addStore(op: JsonObject, raw: ShoppingState, current: ShoppingState, change: Edit): NamespaceStep {
        // A store name is shown inside screen text, so characters that make links or bold there are dropped.
        val name = line(string(op["name"])).replace(MARKUP, "").replace(SPACES, " ").trim()
        if (name.isEmpty() || name.length > MAX_STORE_NAME) return reject(RejectCode.INVALID)
        val storeId = string(op["storeId"])?.takeIf { ID.matches(it) } ?: return reject(RejectCode.INVALID)
        val lower = name.lowercase()
        if (current.stores.any { it.name.lowercase() == lower || it.nameZh == name }) return reject(RejectCode.CONFLICT)
        if (current.stores.size >= MAX_STORES) return reject(RejectCode.CONFLICT)
        if (raw.stores.any { it.id == storeId }) return reject(RejectCode.CONFLICT)
        return change.record(
            raw.copy(stores = raw.stores + ShoppingStore(storeId, name, custom = true, createdBy = change.actor)),
        )
    }

    /** One thing to add, checked with the Shopping screen's rules (Homemind's `parseShoppingBody`). */
    private class NewEntry(val id: String, val storeId: String, val name: String, val qty: Int?, val note: String?) {
        fun item(actor: String, at: String) = ShoppingItem(
            id = id,
            storeId = storeId,
            name = name,
            qty = qty ?: 1,
            note = note,
            addedBy = actor,
            addedAt = at,
        )
    }

    @Suppress("ReturnCount")
    private fun entry(op: JsonObject): NewEntry? {
        val id = string(op["id"])?.takeIf { ID.matches(it) } ?: return null
        val storeId = string(op["storeId"])?.takeIf { ID.matches(it) } ?: return null
        val name = line(string(op["name"]))
        if (name.isEmpty() || name.length > MAX_NAME) return null
        if (op["note"] != null && op["note"] !is JsonNull && string(op["note"]) == null) return null
        val note = line(string(op["note"]))
        if (note.length > MAX_NOTE) return null
        val qty = if (op["qty"] == null) null else qty(op["qty"]) ?: return null
        return NewEntry(id, storeId, name, qty, note.ifEmpty { null })
    }

    private fun item(op: JsonObject, current: ShoppingState): ShoppingItem? =
        string(op["itemId"])?.let { id -> current.items.firstOrNull { it.id == id } }

    private fun store(op: JsonObject, current: ShoppingState): ShoppingStore? =
        string(op["storeId"])?.let { id -> current.stores.firstOrNull { it.id == id } }

    private fun isRunTask(task: Item?, actor: String): Boolean =
        task != null && task.status == ItemStatus.OPEN && (task.ownership as? Ownership.Assigned)?.ownerId == actor

    private fun isParent(context: NamespaceContext, id: String) =
        context.family.members.any { it.id == id && it.role == FamilyRole.PARENT }

    /** A change being written: [record] keeps an Undo record of it, [keep] does not (a run). */
    class Edit(val before: ShoppingState, val opId: String, val at: String, val actor: String) {
        fun same(): NamespaceStep = NamespaceStep.Applied(encode(before))

        fun keep(next: ShoppingState): NamespaceStep = NamespaceStep.Applied(encode(next))

        fun record(next: ShoppingState): NamespaceStep = keep(ShoppingUndoing.withUndo(before, next, opId, actor, at))
    }

    companion object {
        const val NAME = "shopping"
        val KINDS = setOf(
            "item.add", "item.addMany", "item.remove", "item.buy", "item.bump", "item.unbump", "item.setQty",
            "list.clearBought", "run.take", "run.release", "store.add", "store.remove", "undo",
        )
        const val MAX_STORES = 20
        const val MAX_ITEMS = 300
        const val MAX_NAME = 80
        const val MAX_NOTE = 120
        const val MAX_STORE_NAME = 40
        const val MAX_QTY = 99
        const val MAX_UNDO = 30
        const val MAX_CHAT_ITEMS = 10
        private val ID = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")
        private val CONTROL = Regex("[\\u0000-\\u001f\\u007f]")
        private val SPACES =
            Regex("[\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]+")
        private val MARKUP = Regex("[\\[\\]()*`_\\\\]")

        internal val json = Json {
            ignoreUnknownKeys = false
            encodeDefaults = false
            explicitNulls = false
        }

        fun encode(state: ShoppingState): JsonObject = json.encodeToJsonElement(
            ShoppingState.serializer(),
            state,
        ) as JsonObject

        internal fun reject(code: RejectCode): NamespaceStep = NamespaceStep.Rejected(code)

        private fun string(x: JsonElement?): String? = (x as? JsonPrimitive)?.takeIf { it.isString }?.content

        /** One line of the family's own words: control characters out, spaces collapsed, trimmed (JavaScript's). */
        private fun line(value: String?): String {
            if (value == null) return ""
            val collapsed = value.replace(CONTROL, " ").replace(SPACES, " ")
            return collapsed.trim { it == ' ' }
        }

        private fun qty(x: JsonElement?): Int? {
            val n = (x as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.doubleOrNull ?: return null
            if (n != Math.floor(n) || n < 1 || n > MAX_QTY) return null
            return n.toInt()
        }

        /** What `item.add` and `item.addMany` compare: case and spaces don't make it a different item. */
        fun duplicate(state: ShoppingState, storeId: String, name: String): ShoppingItem? {
            fun norm(s: String) = s.lowercase().replace(SPACES, " ").trim { it == ' ' }
            val wanted = norm(name)
            return state.items.firstOrNull {
                val zh = it.nameZh
                val sameName = norm(it.name) == wanted || (zh != null && norm(zh) == wanted)
                it.storeId == storeId && it.bought == null && sameName
            }
        }
    }
}

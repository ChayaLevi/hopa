package com.hopa.family.board

import com.hopa.family.board.BoardRejection.Companion.reject
import com.hopa.family.model.Activity
import com.hopa.family.model.BoardOp
import com.hopa.family.model.BoardState
import com.hopa.family.model.Item
import com.hopa.family.model.LogEntry
import com.hopa.family.model.Partition
import com.hopa.family.model.UndoEntry
import com.hopa.family.model.UndoPatch
import com.hopa.family.model.Undone
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The family board reducer: applies one log entry to one device's state. Pure: no clock, no
 * random, no I/O, and the input is never changed. Every device that replays the same entries in
 * seq order reaches the same state. A port of Homemind's `applyChange` (`server/homemind/core.ts`)
 * under HOPA's rules; `spec/fixtures/family/board-*.json` is the contract.
 *
 * The order of the checks decides the code and is part of the contract
 * (`spec/fixtures/family/README.md`):
 *  1. an unknown `kind` or a `v` other than 1 stops replay (D15);
 *  2. a malformed `opId` or `at` is invalid; an `opId` already applied in the scope is a conflict;
 *  3. an op the schema refuses is invalid;
 *  4. the actor, the server-attested `memberId` (D12), must be a member with an account;
 *  5. the change runs against its own scope only (D11), with ids and time from the op (D10);
 *  6. a split's new part ids are checked last: invalid, repeated (invalid) or taken (conflict).
 */
object BoardReducer {
    fun apply(state: BoardState, entry: LogEntry): BoardStep {
        val raw = entry.op
        val v = (raw["v"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        val kind = (raw["kind"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (v != BoardOp.VERSION || kind == null || kind !in BoardOp.KINDS) return BoardStep.Stopped
        val partition = requireNotNull(state.partition(entry.scope)) {
            "seq ${entry.seq}: this device does not hold scope ${entry.scope}"
        }
        return try {
            BoardStep.Applied(state.withPartition(entry.scope, applyTo(state, partition, entry)))
        } catch (rejection: BoardRejection) {
            BoardStep.Rejected(rejection.code)
        }
    }

    private fun applyTo(state: BoardState, partition: Partition, entry: LogEntry): Partition {
        val raw = entry.op
        val opId = (raw["opId"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (!CreatePlanner.isId(opId)) reject(RejectCode.INVALID)
        if (partition.activity.any { it.id == opId }) reject(RejectCode.CONFLICT)
        val at = (raw["at"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (at == null || !BoardTime.isCanonical(at)) reject(RejectCode.INVALID)
        val op = decode(raw)

        val actor = entry.memberId
        val member = state.family.members.firstOrNull { it.id == actor }
        if (member == null || !member.hasAccount) reject(RejectCode.FORBIDDEN)

        val view = ScopeView(state.family, entry.scope, partition.items, partition.activity)
        val draft = when (op) {
            is BoardOp.Create -> CreatePlanner.create(view, op.item, actor)
            is BoardOp.Undo -> UndoPlanner.plan(view, op.targetOpId, actor)
            is BoardOp.Split -> CreatePlanner.split(view, op, actor).also { checkPartIds(view, op) }
            is BoardOp.Edit -> ItemChangePlanner.plan(view, op, actor, clearsRecurrence = clearsRecurrence(raw))
            is BoardOp.OnItem -> ItemChangePlanner.plan(view, op, actor, clearsRecurrence = false)
        }
        return commit(partition, draft, actor, op.opId, op.at)
    }

    private fun decode(raw: JsonObject): BoardOp = try {
        BoardJson.json.decodeFromJsonElement(BoardOp.serializer(), raw)
    } catch (_: SerializationException) {
        reject(RejectCode.INVALID)
    } catch (_: IllegalArgumentException) {
        reject(RejectCode.INVALID)
    }

    /** `"recurrence": null` in an edit's patch stops the repetition; an absent one changes nothing. */
    private fun clearsRecurrence(raw: JsonObject): Boolean =
        (raw["patch"] as? JsonObject)?.get("recurrence") is JsonNull

    private fun checkPartIds(view: ScopeView, op: BoardOp.Split) {
        val ids = op.parts.drop(1).map { it.id }
        if (ids.any { !CreatePlanner.isId(it) }) reject(RejectCode.INVALID)
        if (ids.toSet().size != ids.size) reject(RejectCode.INVALID)
        if (ids.any { view.find(it) != null }) reject(RejectCode.CONFLICT)
    }

    /** Writes the new versions, the activity (id = opId, time = at) and the undo patch. */
    private fun commit(partition: Partition, draft: Draft, actor: String, opId: String, at: String): Partition {
        val byId = partition.items.associateBy { it.id }
        val finalized = linkedMapOf<String, Item?>()
        val undoEntries = mutableListOf<UndoEntry>()
        for ((id, next) in draft.next) {
            val before = byId[id]
            val after = next?.copy(version = before?.let { it.version + 1 } ?: next.version)
            finalized[id] = after
            undoEntries += UndoEntry(itemId = id, before = before, afterVersion = after?.version ?: 0)
        }
        val items = partition.items.mapNotNull { item -> if (item.id in finalized) finalized[item.id] else item } +
            finalized.filter { (id, after) -> after != null && id !in byId }.values.filterNotNull()

        val activity = Activity(
            id = opId,
            at = at,
            actor = actor,
            what = draft.what,
            kind = draft.kind,
            itemId = draft.itemId?.takeIf { it.isNotEmpty() },
            memberId = draft.memberId?.takeIf { it.isNotEmpty() },
            note = draft.note?.takeIf { it.isNotEmpty() },
            ref = draft.ref?.takeIf { it.isNotEmpty() },
            undo = if (draft.undoes == null) UndoPatch(undoEntries) else null,
        )
        val log = partition.activity.map { if (it === draft.undoes) it.copy(undone = Undone(at, actor, opId)) else it }
        // Other namespaces' state in this scope is not the board's to change (D11 for capabilities).
        return partition.copy(items = items, activity = log + activity)
    }
}

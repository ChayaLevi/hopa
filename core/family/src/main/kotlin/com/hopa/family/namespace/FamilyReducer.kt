package com.hopa.family.namespace

import com.hopa.family.board.BoardReducer
import com.hopa.family.board.BoardStep
import com.hopa.family.board.RejectCode
import com.hopa.family.model.BoardOp
import com.hopa.family.model.BoardState
import com.hopa.family.model.LogEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Applies one log entry, whatever capability it belongs to (amended D15):
 *
 *  - `v: 1` is the board: [BoardReducer], unchanged. An unknown board kind stops `board`;
 *  - `v: 2` names its namespace in `ns` and that namespace's version in `nsv`. A namespace this
 *    build does not know is kept aside; a newer `nsv` stops that namespace; `ns: board` is
 *    refused (the board writes v1);
 *  - any other `v`, or none, stops everything.
 *
 * Pure, like the board reducer: every device that applies the same entries reaches the same state
 * in every namespace it knows.
 */
object FamilyReducer {
    fun apply(state: BoardState, entry: LogEntry, namespaces: Namespaces): FamilyStep {
        val op = entry.op
        return when (version(op, "v")) {
            1 -> when (val step = BoardReducer.apply(state, entry)) {
                is BoardStep.Applied -> FamilyStep.Applied(step.state)
                is BoardStep.Rejected -> FamilyStep.Rejected(step.code)
                BoardStep.Stopped -> FamilyStep.Stopped(Namespaces.BOARD)
            }
            2 -> applyNamespace(state, entry, namespaces)
            else -> FamilyStep.Stopped(null)
        }
    }

    /** The namespace an entry belongs to, as far as the envelope says: `board` for v1, else `ns`. */
    fun namespaceOf(op: JsonObject): String? = when (version(op, "v")) {
        1 -> Namespaces.BOARD
        2 -> (op["ns"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        else -> null
    }

    /** Whether this build can apply [op] (its envelope, namespace, version and kind are known). */
    fun readable(op: JsonObject, namespaces: Namespaces): Boolean {
        val kind = (op["kind"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return false
        return when (version(op, "v")) {
            1 -> kind in BoardOp.KINDS
            2 -> {
                val namespace = namespaceOf(op)?.let { namespaces[it] } ?: return false
                val nsv = version(op, "nsv") ?: return false
                nsv <= namespace.version && namespace.knows(kind, nsv)
            }
            else -> false
        }
    }

    @Suppress("ReturnCount")
    private fun applyNamespace(state: BoardState, entry: LogEntry, namespaces: Namespaces): FamilyStep {
        val op = entry.op
        val name = namespaceOf(op)
        val nsv = version(op, "nsv")
        if (name == null || !Namespaces.NAME.matches(name) || nsv == null || nsv < 1) {
            return FamilyStep.Rejected(RejectCode.INVALID)
        }
        if (name == Namespaces.BOARD) return FamilyStep.Rejected(RejectCode.INVALID)
        val namespace = namespaces[name] ?: return FamilyStep.KeptAside(name)
        if (nsv > namespace.version) return FamilyStep.Stopped(name)
        val partition = requireNotNull(state.partition(entry.scope)) {
            "seq ${entry.seq}: this device does not hold scope ${entry.scope}"
        }
        val member = state.family.members.firstOrNull { it.id == entry.memberId }
        if (member == null || !member.hasAccount) return FamilyStep.Rejected(RejectCode.FORBIDDEN)

        val context = NamespaceContext(
            family = state.family,
            scope = entry.scope,
            actorId = entry.memberId,
            nsv = nsv,
            own = partition.ns?.get(name),
            board = partition.takeIf { Namespaces.BOARD in namespace.reads },
            reads = namespace.reads.filter { it != Namespaces.BOARD }
                .mapNotNull { read -> partition.ns?.get(read)?.let { read to it } }.toMap(),
        )
        return when (val step = namespace.apply(context, op)) {
            is NamespaceStep.Applied -> {
                val ns = (partition.ns.orEmpty() + (name to step.state)).toSortedMap()
                FamilyStep.Applied(state.withPartition(entry.scope, partition.copy(ns = ns)))
            }
            is NamespaceStep.Rejected -> FamilyStep.Rejected(step.code)
            NamespaceStep.Stopped -> FamilyStep.Stopped(name)
        }
    }

    private fun version(op: JsonObject, field: String): Int? =
        (op[field] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
}

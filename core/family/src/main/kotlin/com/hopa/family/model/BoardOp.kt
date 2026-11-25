package com.hopa.family.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonObject

/**
 * One op as the proposing device wrote it (`spec/schemas/family/op.v1.schema.json`). It carries
 * its own `opId`, its time `at` and the id of every item it creates (D10), and no actor (D12).
 * Unknown fields are refused: a new field needs a new `v` (D15).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface BoardOp {
    val v: Int
    val opId: String
    val at: String

    /** An op that names one existing item at the version the proposer saw (D8). */
    sealed interface OnItem : BoardOp {
        val itemId: String
        val version: Int
    }

    @Serializable
    @SerialName("create")
    data class Create(
        override val v: Int,
        override val opId: String,
        override val at: String,
        val item: NewItem,
    ) : BoardOp

    @Serializable
    @SerialName("edit")
    data class Edit(
        override val v: Int,
        override val opId: String,
        override val at: String,
        override val itemId: String,
        override val version: Int,
        val patch: ItemPatch,
    ) : OnItem

    @Serializable
    @SerialName("assign")
    data class Assign(
        override val v: Int,
        override val opId: String,
        override val at: String,
        override val itemId: String,
        override val version: Int,
        val ownerId: String,
    ) : OnItem

    @Serializable
    @SerialName("open")
    data class Open(
        override val v: Int,
        override val opId: String,
        override val at: String,
        override val itemId: String,
        override val version: Int,
        val candidateIds: List<String>? = null,
        val reason: OpenReason? = null,
    ) : OnItem

    @Serializable
    @SerialName("unassign")
    data class Unassign(
        override val v: Int,
        override val opId: String,
        override val at: String,
        override val itemId: String,
        override val version: Int,
    ) : OnItem

    @Serializable
    @SerialName("claim")
    data class Claim(
        override val v: Int,
        override val opId: String,
        override val at: String,
        override val itemId: String,
        override val version: Int,
    ) : OnItem

    @Serializable
    @SerialName("confirm")
    data class Confirm(
        override val v: Int,
        override val opId: String,
        override val at: String,
        override val itemId: String,
        override val version: Int,
    ) : OnItem

    @Serializable
    @SerialName("handback")
    data class Handback(
        override val v: Int,
        override val opId: String,
        override val at: String,
        override val itemId: String,
        override val version: Int,
        val note: String? = null,
    ) : OnItem

    @Serializable
    @SerialName("done")
    data class Done(
        override val v: Int,
        override val opId: String,
        override val at: String,
        override val itemId: String,
        override val version: Int,
    ) : OnItem

    @Serializable
    @SerialName("cancel")
    data class Cancel(
        override val v: Int,
        override val opId: String,
        override val at: String,
        override val itemId: String,
        override val version: Int,
    ) : OnItem

    @Serializable
    @SerialName("cancel_occurrence")
    data class CancelOccurrence(
        override val v: Int,
        override val opId: String,
        override val at: String,
        override val itemId: String,
        override val version: Int,
        val date: String,
    ) : OnItem

    @Serializable
    @SerialName("approve")
    data class Approve(
        override val v: Int,
        override val opId: String,
        override val at: String,
        override val itemId: String,
        override val version: Int,
    ) : OnItem

    @Serializable
    @SerialName("dismiss")
    data class Dismiss(
        override val v: Int,
        override val opId: String,
        override val at: String,
        override val itemId: String,
        override val version: Int,
    ) : OnItem

    @Serializable
    @SerialName("split")
    data class Split(
        override val v: Int,
        override val opId: String,
        override val at: String,
        override val itemId: String,
        override val version: Int,
        val parts: List<SplitPart>,
        val source: Source? = null,
    ) : OnItem

    @Serializable
    @SerialName("undo")
    data class Undo(
        override val v: Int,
        override val opId: String,
        override val at: String,
        val targetOpId: String,
    ) : BoardOp

    companion object {
        const val VERSION = 1

        /** The kinds a v1 reader knows. Any other kind stops replay (D15). */
        val KINDS: Set<String> = setOf(
            "create", "edit", "assign", "open", "unassign", "claim", "confirm", "handback",
            "done", "cancel", "cancel_occurrence", "approve", "dismiss", "split", "undo",
        )
    }
}

/** The item a create adds. Its `id` is minted by the proposing device (D10). */
@Serializable
data class NewItem(
    val id: String,
    val type: ItemType,
    val title: String,
    val notes: String? = null,
    val start: String? = null,
    val end: String? = null,
    val allDay: Boolean? = null,
    val forMemberIds: List<String>? = null,
    val sensitive: Boolean? = null,
    val importance: Importance? = null,
    val unavailableMemberIds: List<String>? = null,
    val recurrence: Recurrence? = null,
    val parentEventId: String? = null,
    val ownership: OwnershipInput? = null,
    val source: Source? = null,
    val proposed: Boolean? = null,
)

/**
 * The fields an edit changes. An empty `start` or `end` clears it. A `recurrence` of JSON null
 * stops the repetition; the reducer tells it from an absent one by the op's JSON.
 */
@Serializable
data class ItemPatch(
    val title: String? = null,
    val notes: String? = null,
    val start: String? = null,
    val end: String? = null,
    val allDay: Boolean? = null,
    val forMemberIds: List<String>? = null,
    val sensitive: Boolean? = null,
    val importance: Importance? = null,
    val unavailableMemberIds: List<String>? = null,
    val recurrence: Recurrence? = null,
)

/** A split part. Every part after the first carries the id of the new item (D10). */
@Serializable
data class SplitPart(
    val id: String? = null,
    val title: String,
    val start: String? = null,
)

/** Ownership a create may set. `confirmed` is not an input: the reducer sets it. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("state")
sealed interface OwnershipInput {
    @Serializable
    @SerialName("none")
    data object None : OwnershipInput

    @Serializable
    @SerialName("open")
    data class Open(
        val candidateIds: List<String>? = null,
        val reason: OpenReason? = null,
    ) : OwnershipInput

    @Serializable
    @SerialName("assigned")
    data class Assigned(
        val ownerId: String,
    ) : OwnershipInput
}

/**
 * What the reducer is fed for one seq: the fields the server attests and the decrypted op, kept
 * as JSON so an op of an unknown kind or version can be recognised and stop the replay (D15).
 */
@Serializable
data class LogEntry(
    val seq: Long,
    val scope: Scope,
    val memberId: String,
    val fromDevice: String,
    val op: JsonObject,
)

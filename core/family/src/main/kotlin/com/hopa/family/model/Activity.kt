package com.hopa.family.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One applied op. Its id is the op's `opId`, its time the op's `at`, its actor the member the
 * server attested for that seq (D10, D12). `undo` holds what Undo needs; it is absent on an undo.
 */
@Serializable
data class Activity(
    val id: String,
    val at: String,
    val actor: String,
    val what: String,
    val kind: ActivityKind,
    val itemId: String? = null,
    val memberId: String? = null,
    val note: String? = null,
    val ref: String? = null,
    val undo: UndoPatch? = null,
    val undone: Undone? = null,
)

@Serializable
data class UndoPatch(
    val items: List<UndoEntry>,
)

/** The item before the op (null when the op created it) and its version right after (0: removed). */
@Serializable
data class UndoEntry(
    val itemId: String,
    val before: Item?,
    val afterVersion: Int,
)

@Serializable
data class Undone(
    val at: String,
    val by: String,
    val activityId: String,
)

/** A split is recorded as [EDIT]. */
@Serializable
enum class ActivityKind {
    @SerialName("create")
    CREATE,

    @SerialName("edit")
    EDIT,

    @SerialName("assign")
    ASSIGN,

    @SerialName("open")
    OPEN,

    @SerialName("unassign")
    UNASSIGN,

    @SerialName("claim")
    CLAIM,

    @SerialName("confirm")
    CONFIRM,

    @SerialName("handback")
    HANDBACK,

    @SerialName("done")
    DONE,

    @SerialName("cancel")
    CANCEL,

    @SerialName("cancel_occurrence")
    CANCEL_OCCURRENCE,

    @SerialName("approve")
    APPROVE,

    @SerialName("dismiss")
    DISMISS,

    @SerialName("undo")
    UNDO,
}

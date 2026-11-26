package com.hopa.family.scope

/** The result of planning a visibility change. */
sealed interface MovePlan {
    /** The ops to send, in this order: the tombstone first, then the new items. */
    data class Ops(val ops: List<ScopedOp>) : MovePlan

    /** Nothing to send, and why. Screens show the reason by its code. */
    data class Refused(val reason: MoveRefusal) : MovePlan
}

enum class MoveRefusal {
    /** The item is not in the source scope on this device. */
    NOT_FOUND,

    /** The member may not write one of the two scopes, or the scopes are the same. */
    NOT_ALLOWED,

    /** A done or cancelled item stays where it is; it is history. */
    CLOSED,

    /** A preparation moves with its event, never alone, or it would name an event in another scope. */
    PREPARATION,
}

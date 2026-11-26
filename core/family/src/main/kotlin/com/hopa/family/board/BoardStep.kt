package com.hopa.family.board

import com.hopa.family.model.BoardState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What one log entry did to a device's state. */
sealed interface BoardStep {
    /** The op was applied; [state] is the new state. */
    data class Applied(val state: BoardState) : BoardStep

    /** The op was refused the same way on every device; the state is unchanged. */
    data class Rejected(val code: RejectCode) : BoardStep

    /** An op of an unknown kind or a higher `v`: replay stops at this seq, "update required" (D15). */
    data object Stopped : BoardStep
}

/** Stable codes, the same as Homemind's `ChangeErrorCode`. Screens read the code, never a message. */
@Serializable
enum class RejectCode {
    @SerialName("invalid")
    INVALID,

    @SerialName("forbidden")
    FORBIDDEN,

    @SerialName("not_found")
    NOT_FOUND,

    @SerialName("stale")
    STALE,

    @SerialName("conflict")
    CONFLICT,
}

/** The outcome of one entry the replay reached. */
@Serializable
data class Outcome(
    val seq: Long,
    val outcome: String,
    val code: RejectCode? = null,
) {
    companion object {
        fun applied(seq: Long) = Outcome(seq, "applied")

        fun rejected(seq: Long, code: RejectCode) = Outcome(seq, "rejected", code)
    }
}

/** A replay: the final state, one outcome per entry reached, and the seq it stopped at, if any. */
data class ReplayResult(
    val state: BoardState,
    val outcomes: List<Outcome>,
    val stoppedAt: Long? = null,
)

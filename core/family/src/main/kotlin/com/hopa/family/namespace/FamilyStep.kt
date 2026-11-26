package com.hopa.family.namespace

import com.hopa.family.board.RejectCode
import com.hopa.family.model.BoardState

/** What one log entry did on a device, across the board and every namespace. */
sealed interface FamilyStep {
    data class Applied(val state: BoardState) : FamilyStep

    data class Rejected(val code: RejectCode) : FamilyStep

    /** A namespace this build does not know: keep the entry aside, apply nothing of it. */
    data class KeptAside(val namespace: String) : FamilyStep

    /**
     * Stop [namespace] (and its readers) at this seq: an unknown kind or a newer `nsv`. A null
     * namespace stops everything: an envelope `v` this build does not know.
     */
    data class Stopped(val namespace: String?) : FamilyStep
}

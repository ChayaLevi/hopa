package com.hopa.family.availability

import com.hopa.family.board.RejectCode
import com.hopa.family.scope.ScopedOp

/** The ops that open a scope's conflicts, or why they cannot be written. */
sealed interface Proposal {
    data class Ops(val ops: List<ScopedOp>, val openedItemIds: List<String>) : Proposal

    data class Refused(val code: RejectCode) : Proposal
}

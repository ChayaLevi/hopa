package com.hopa.family.namespace

import com.hopa.family.board.RejectCode
import kotlinx.serialization.json.JsonObject

/** What one op did to its namespace's state. */
sealed interface NamespaceStep {
    /** The namespace's new state in this scope. */
    data class Applied(val state: JsonObject) : NamespaceStep

    /** Refused the same way on every device; nothing changes. */
    data class Rejected(val code: RejectCode) : NamespaceStep

    /** A kind this build of the namespace does not know: the namespace stops at this seq. */
    data object Stopped : NamespaceStep
}

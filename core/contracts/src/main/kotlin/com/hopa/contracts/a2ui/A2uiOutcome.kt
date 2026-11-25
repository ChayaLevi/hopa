package com.hopa.contracts.a2ui

/** What applying one message did. */
sealed interface A2uiOutcome {
    /** The surface is complete and valid: draw it. */
    data class Ready(val surface: A2uiSurface) : A2uiOutcome

    /** Accepted, but there is no `root` yet: keep waiting. */
    data class Pending(val surfaceId: String) : A2uiOutcome

    data class Deleted(val surfaceId: String) : A2uiOutcome

    /** Nothing changed; answer with [A2uiCodec.errorFor]. */
    data class Refused(val surfaceId: String?, val problem: A2uiProblem) : A2uiOutcome
}

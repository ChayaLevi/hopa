package com.hopa.contracts.a2ui

/** The error codes HOPA sends in an A2UI `error` message. */
object A2uiCode {
    const val VALIDATION_FAILED = "VALIDATION_FAILED"
    const val UNALLOWED_PARENT = "UNALLOWED_PARENT"
    const val UNALLOWED_CHILD = "UNALLOWED_CHILD"
    const val INTEGRITY_ERROR = "INTEGRITY_ERROR"

    /** A real A2UI 1.0 feature that HOPA refuses, or another version. */
    const val UNSUPPORTED = "UNSUPPORTED"
}

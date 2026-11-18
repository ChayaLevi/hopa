package com.hopa.capabilities.display.client

/** What the background-display helper is doing, as the app sees it. */
sealed interface HelperStatus {
    /** No socket, and nobody has asked for a helper yet. */
    data object Stopped : HelperStatus

    /** A start was asked for through adb; the socket is not up yet. */
    data object Starting : HelperStatus

    /** The helper answered a round trip. */
    data object Running : HelperStatus

    /** It did not start, or it stopped answering. [reason] is for the person. */
    data class Failed(val reason: String) : HelperStatus
}

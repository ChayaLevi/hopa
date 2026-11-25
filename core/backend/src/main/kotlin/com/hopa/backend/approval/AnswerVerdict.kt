package com.hopa.backend.approval

/** What [AnswerCheck] decided, with a stable code the fixtures and the logs use. */
sealed interface AnswerVerdict {
    val code: String

    data class Allowed(override val code: String) : AnswerVerdict

    data class Denied(override val code: String) : AnswerVerdict

    companion object {
        /** Signed by the key this device pinned earlier. */
        const val VERIFIED = "verified"

        /** The first signed answer from this device: its key is pinned now. */
        const val PINNED = "pinned"

        /** Unsigned, from a device that has never signed: a client from before signing. */
        const val LEGACY = "legacy"

        const val DENIED = "denied"
        const val EXPIRED = "expired"
        const val UNSIGNED = "unsigned"
        const val MALFORMED = "malformed"
        const val NO_SENDER = "no_sender"
        const val BAD_SIGNATURE = "bad_signature"
        const val KEY_CHANGED = "key_changed"
    }
}

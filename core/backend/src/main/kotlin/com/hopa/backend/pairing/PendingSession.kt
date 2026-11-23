package com.hopa.backend.pairing

/** The one-time answer to a pending mint; the secret lives only in the QR. */
data class PendingSession(
    val sessionId: String,
    val secret: String,
    val qr: String,
    val expiresAt: String,
) {
    /** The secret and QR contents are capabilities until the session ends. */
    override fun toString(): String = "PendingSession(sessionId=$sessionId, secret=<redacted>, qr=<redacted>, " +
        "expiresAt=$expiresAt)"
}

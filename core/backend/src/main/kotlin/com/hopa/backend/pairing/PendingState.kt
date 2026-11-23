package com.hopa.backend.pairing

/** What a poller may learn by presenting the session secret. */
data class PendingState(
    val state: String,
    val name: String,
    val publicKey: String,
    val fingerprint: String,
    val expiresAt: String,
)

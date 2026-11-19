package com.hopa.bridge.session

/** What `/pair` and `/refresh` return from the bridge contract. */
data class BridgeSession(
    val token: String,
    val refresh: String,
    val expiresIn: Long,
)

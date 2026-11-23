package com.hopa.backend.pairing

/** A claim's tokens plus the household key wrap and the phone key it names. */
data class ClaimedSession(
    val credentials: BackendCredentials,
    val wrappedKey: String?,
    val voucherPublicKey: String?,
)

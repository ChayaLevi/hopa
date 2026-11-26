package com.hopa.backend.board

/**
 * One device's wrap of a scope key at [keyEpoch], with the device that wrapped
 * it and that device's public key: the recipient needs both to unwrap
 * (static-static X25519, ADR-0033 §3).
 */
class ScopeKeyWrap(
    val scope: String,
    val keyEpoch: Int,
    val wrappedKey: ByteArray,
    val wrapperDeviceId: String?,
    val wrapperPublicKey: ByteArray?,
)

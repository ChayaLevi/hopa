package com.hopa.backend.board

/**
 * A device asks to store [wrappedKey] for [targetDeviceId] at [keyEpoch].
 * [callerDeviceId] comes from the access token, never from the body.
 */
class WrapRequest(
    val scope: String,
    val callerDeviceId: String,
    val targetDeviceId: String,
    val keyEpoch: Int,
    val wrappedKey: ByteArray,
)

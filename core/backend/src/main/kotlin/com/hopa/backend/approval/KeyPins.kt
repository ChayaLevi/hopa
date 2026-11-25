package com.hopa.backend.approval

/** The answering devices' pinned signing keys, by gateway device id. */
interface KeyPins {
    fun pinned(deviceId: String): ByteArray?

    fun pin(deviceId: String, key: ByteArray)
}

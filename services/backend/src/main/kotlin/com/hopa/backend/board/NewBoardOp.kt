package com.hopa.backend.board

import java.security.MessageDigest

/**
 * An op a device asks to append. [fromDevice] and [memberId] come from the
 * access token, never from the wire (D12); [body] is ciphertext the server
 * never reads.
 */
class NewBoardOp(
    val opId: String,
    val scope: String,
    val fromDevice: String,
    val memberId: String,
    val keyEpoch: Int,
    val body: ByteArray,
) {
    /** SHA-256 of the ciphertext: what tells a replay from a conflicting reuse of an op id. */
    val bodyHash: ByteArray by lazy { MessageDigest.getInstance("SHA-256").digest(body) }
}

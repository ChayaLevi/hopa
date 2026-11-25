package com.hopa.backend.board

/** One stored op: the server-assigned [seq], the attested actor and the ciphertext. */
class BoardOp(
    val seq: Long,
    val scope: String,
    val opId: String,
    val fromDevice: String,
    val memberId: String,
    val keyEpoch: Int,
    val body: ByteArray,
    val createdAt: Long,
)

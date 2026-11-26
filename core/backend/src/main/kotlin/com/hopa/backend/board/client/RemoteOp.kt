package com.hopa.backend.board.client

/** One op as the server stores it: what it attests, and the ciphertext it never read. */
data class RemoteOp(
    val seq: Long,
    val opId: String,
    val fromDevice: String,
    val memberId: String,
    val at: String,
    val keyEpoch: Int,
    val ct: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is RemoteOp && other.seq == seq && other.opId == opId &&
        other.ct.contentEquals(ct)

    override fun hashCode(): Int = 31 * seq.hashCode() + opId.hashCode()
}

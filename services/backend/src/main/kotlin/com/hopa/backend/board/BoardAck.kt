package com.hopa.backend.board

/** The answer to an append: the op's seq, and whether it had been stored before. */
data class BoardAck(val scope: String, val seq: Long, val duplicate: Boolean)

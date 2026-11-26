package com.hopa.backend.board.client

/** A failure the board link could not turn into an answer. */
class BoardLinkException(val status: Int, val code: String?) : RuntimeException("board call failed: $status $code")

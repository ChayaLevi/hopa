package com.hopa.backend.board.client

/** One HTTP answer: the status and the body text (empty when there is none). */
data class HttpAnswer(
    val status: Int,
    val body: String,
)

package com.hopa.backend.board.client

data class BoardPage(
    val scope: String,
    val ops: List<RemoteOp>,
    val hasMore: Boolean,
)

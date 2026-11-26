package com.hopa.backend.board

/** One page of ops, ascending by seq; [hasMore] when ops remain after the last one. */
class BoardPage(val ops: List<BoardOp>, val hasMore: Boolean)

package com.hopa.family.scope

import com.hopa.family.model.BoardOp
import com.hopa.family.model.Scope

/** An op to send, and the scope (so the key) it goes under. */
data class ScopedOp(
    val scope: Scope,
    val op: BoardOp,
)

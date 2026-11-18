package com.hopa.engine.execution

import com.hopa.contracts.HopaError
import com.hopa.contracts.wire.Decision

data class ParseOutcome(
    val decision: Decision? = null,
    val text: String? = null,
    val error: HopaError? = null,
)

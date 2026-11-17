package com.hopa.engine.parser

import com.hopa.engine.contracts.Decision
import com.hopa.engine.contracts.HopaError

data class ParseOutcome(
    val decision: Decision? = null,
    val text: String? = null,
    val error: HopaError? = null,
)

package com.hopa.engine.parser

import com.hopa.contracts.Decision
import com.hopa.contracts.HopaError

data class ParseOutcome(
    val decision: Decision? = null,
    val text: String? = null,
    val error: HopaError? = null,
)

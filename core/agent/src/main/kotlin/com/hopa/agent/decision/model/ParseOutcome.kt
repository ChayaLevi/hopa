package com.hopa.agent.decision.model

import com.hopa.contracts.error.HopaError
import com.hopa.contracts.wire.Decision

data class ParseOutcome(
    val decision: Decision? = null,
    val text: String? = null,
    val error: HopaError? = null,
)

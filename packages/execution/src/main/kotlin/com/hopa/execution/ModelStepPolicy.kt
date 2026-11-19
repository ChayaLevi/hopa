package com.hopa.execution

import com.hopa.contracts.model.ModelPort
import com.hopa.contracts.model.ModelReply
import com.hopa.contracts.model.ModelRequest

/** The provider the client configured: one request, one reply. */
class ModelStepPolicy(
    private val model: ModelPort,
) : StepPolicy {
    override suspend fun next(request: ModelRequest): ModelReply = model.complete(request)
}

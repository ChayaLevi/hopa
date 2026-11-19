package com.hopa.execution

import com.hopa.contracts.model.ModelReply
import com.hopa.contracts.model.ModelRequest

/**
 * Where the next step comes from. The loop asks for one reply and parses it; it
 * does not care whether a model wrote it.
 *
 *  - `ModelStepPolicy` asks the provider today.
 *  - A playbook policy will answer from anchors and a recipe when there is no
 *    model at all, and stop with a question when the screen does not match.
 *
 * The request carries the system prompt, the budgeted history, the goal and the
 * rendered screen, so an implementation that never calls out still has what it
 * needs to choose a step or refuse.
 */
interface StepPolicy {
    suspend fun next(request: ModelRequest): ModelReply
}

package com.hopa.turn.plan

import com.hopa.contracts.model.ModelMessage
import com.hopa.contracts.model.ModelPort
import com.hopa.contracts.model.ModelRequest

/** Asks the model for a plan; never acts, and an empty answer is not a crash. */
class ModelPlanSource(
    private val model: ModelPort,
) : PlanSource {
    override suspend fun plan(goal: String): List<String> {
        val reply = runCatching {
            model.complete(
                ModelRequest(
                    messages = listOf(
                        ModelMessage("system", PROMPT),
                        ModelMessage("user", goal),
                    ),
                    maxTokens = 400,
                ),
            ).text
        }.getOrNull().orEmpty()

        return reply.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { it.replaceFirst(Regex("^\\d+[.)]\\s*"), "") }
            .take(MAX_STEPS)
            .toList()
    }

    private companion object {
        const val MAX_STEPS = 6
        const val PROMPT =
            "You are planning, not acting. Reply with a numbered plan of at most six " +
                "short steps, one per line, no other text."
    }
}

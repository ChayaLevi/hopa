package com.hopa.turn.plan

import com.hopa.contracts.model.ModelMessage
import com.hopa.contracts.model.ModelPort
import com.hopa.contracts.model.ModelRequest

/**
 * Asks the model for a plan; never acts, and an empty answer is not a crash.
 *
 * [environment] is the client's description of the device - platform, screen
 * mode, installed apps. Without it the planner wrote steps like "identify the
 * device and OS" and asked to see a screen it cannot see, because nothing told
 * it that HOPA runs on an Android phone.
 */
class ModelPlanSource(
    private val model: ModelPort,
    private val environment: suspend () -> String = { "" },
) : PlanSource {
    override suspend fun plan(goal: String): List<String> {
        val where = runCatching { environment() }.getOrNull().orEmpty().trim()
        val system = if (where.isEmpty()) PROMPT else PROMPT + "\n\n" + where
        val reply = runCatching {
            model.complete(
                ModelRequest(
                    messages = listOf(
                        ModelMessage("system", system),
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

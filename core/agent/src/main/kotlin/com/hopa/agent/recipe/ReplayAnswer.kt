package com.hopa.agent.recipe

import com.hopa.agent.ModelReplyText
import com.hopa.contracts.model.ModelMessage
import com.hopa.contracts.model.ModelPort
import com.hopa.contracts.model.ModelRequest
import com.hopa.contracts.wire.Observation
import com.hopa.agent.prompt.ScreenRenderer

/**
 * Answers the person after a replay. The recipe carries the taps, not the words:
 * "The first row is Wi-Fi" came from reading the screen, so one cheap model call
 * with the final tree turns "repeated 2 steps" into the actual answer.
 *
 * No action can come out of this call - it is given a render of the screen and
 * asked to answer - and a failure here is not a failed run: the caller falls back
 * to the plain replay sentence.
 */
class ReplayAnswer(
    private val model: ModelPort,
    private val renderer: ScreenRenderer = ScreenRenderer(),
) {
    /** The answer and the tokens it cost; the pair is what the receipt shows. */
    suspend fun answer(goal: String, observation: Observation): Pair<String, Int>? {
        val request = ModelRequest(
            messages = listOf(
                ModelMessage(
                    "user",
                    "The recipe for this request has already run. The person asked: $goal\n\n" +
                        "The screen now is:\n${renderer.render(observation)}\n\n" +
                        "Answer the person's question in one or two short sentences, using only " +
                        "what this screen shows. Plain text, no markdown. If the answer is not " +
                        "on it, say what you see and that you could not find it. Do not ask to " +
                        "run anything.",
                ),
            ),
        )
        val reply = runCatching { model.complete(request) }.getOrNull() ?: return null
        // The bubble shows plain text, so markup the model adds anyway is dropped.
        val text = ModelReplyText.clean(reply.text)
            .replace("**", "")
            .replace("__", "")
            .trim()
        if (text.isEmpty()) {
            return null
        }
        return text to reply.usage.totalTokens
    }
}

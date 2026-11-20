package com.hopa.inference.local

/**
 * Fits a prompt into a small context window.
 *
 * The order is fixed and visible: the catalogue shrinks to the twelve most
 * relevant apps, skills to three, facts to eight, history to the last four
 * turns, and the screen summary is cut to its first two fields. Instructions
 * and the task survive untouched, because a model that forgets what it was
 * asked is useless even if the prompt fits.
 */
class PromptBudget(
    contextTokens: Int,
) {
    val inputCeiling: Int = (contextTokens * INPUT_SHARE).toInt().coerceAtLeast(MIN_INPUT)
    val replyCeiling: Int = (contextTokens / REPLY_SHARE).coerceIn(MIN_REPLY, MAX_REPLY)

    fun fit(parts: PromptParts): PromptParts {
        var fitted = parts
        if (fitted.estimatedTokens() <= inputCeiling) return fitted

        fitted = fitted.copy(apps = fitted.apps.take(MAX_APPS))
        fitted = fitted.copy(skills = fitted.skills.take(MAX_SKILLS))
        fitted = fitted.copy(facts = fitted.facts.take(MAX_FACTS))
        fitted = fitted.copy(history = fitted.history.takeLast(MAX_HISTORY))
        fitted = fitted.copy(screen = fitted.screen.take(MAX_SCREEN_CHARS))

        while (fitted.estimatedTokens() > inputCeiling) {
            val shortened = when {
                fitted.apps.isNotEmpty() -> fitted.copy(apps = fitted.apps.dropLast(1))
                fitted.facts.isNotEmpty() -> fitted.copy(facts = fitted.facts.dropLast(1))
                fitted.skills.isNotEmpty() -> fitted.copy(skills = fitted.skills.dropLast(1))
                fitted.history.isNotEmpty() -> fitted.copy(history = fitted.history.drop(1))
                else -> break
            }
            fitted = shortened
        }
        return fitted
    }

    private companion object {
        const val INPUT_SHARE = 0.6
        const val MIN_INPUT = 2_000
        const val REPLY_SHARE = 4
        const val MIN_REPLY = 256
        const val MAX_REPLY = 1_024
        const val MAX_APPS = 12
        const val MAX_SKILLS = 3
        const val MAX_FACTS = 8
        const val MAX_HISTORY = 4
        const val MAX_SCREEN_CHARS = 600
    }
}

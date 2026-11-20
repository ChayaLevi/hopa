package com.hopa.inference.local

/**
 * The parts a prompt is assembled from, in the order they matter.
 *
 * A phone model has 4–8k of context instead of 128k, so the budget trims the
 * parts that are cheap to lose first: the catalogue, then skills, then facts,
 * then history, then the screen summary. Instructions and the task itself are
 * never cut.
 */
data class PromptParts(
    val instructions: String = "",
    val task: String = "",
    val facts: List<String> = emptyList(),
    val skills: List<String> = emptyList(),
    val apps: List<String> = emptyList(),
    val screen: String = "",
    val history: List<String> = emptyList(),
) {
    /** A rough token count: four characters per token, which holds for English. */
    fun estimatedTokens(): Int = (
        instructions.length +
            task.length +
            facts.sumOf { it.length } +
            skills.sumOf { it.length } +
            apps.sumOf { it.length } +
            screen.length +
            history.sumOf { it.length }
        ) / 4
}

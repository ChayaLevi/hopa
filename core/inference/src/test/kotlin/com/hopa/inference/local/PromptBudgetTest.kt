package com.hopa.inference.local

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PromptBudgetTest {
    private fun longParts() = PromptParts(
        instructions = "You plan steps for a phone assistant.",
        task = "Open the calculator and add 2 and 2.",
        facts = (1..30).map { "fact number $it with some words" },
        skills = (1..10).map { "skill number $it with some words" },
        apps = (1..40).map { "app number $it with some words" },
        screen = "title and body ".repeat(200),
        history = (1..20).map { "message number $it with some words" },
    )

    @Test
    fun a_roomy_model_keeps_everything() {
        val parts = PromptParts(task = "open the calculator")
        val fitted = PromptBudget(contextTokens = 32_000).fit(parts)

        assertEquals(parts, fitted)
    }

    @Test
    fun a_small_model_trims_catalogue_skills_facts_history_and_screen_first() {
        val budget = PromptBudget(contextTokens = 4_096)
        val oversized = longParts().copy(
            apps = (1..200).map { "app $it " + "x".repeat(60) },
            screen = "title and body ".repeat(2_000),
        )

        val fitted = budget.fit(oversized)

        assertTrue(fitted.estimatedTokens() <= budget.inputCeiling)
        assertTrue(fitted.apps.size <= 12)
        assertTrue(fitted.skills.size <= 3)
        assertTrue(fitted.facts.size <= 8)
        assertTrue(fitted.history.size <= 4)
        assertTrue(fitted.screen.length <= 600)
    }

    @Test
    fun a_prompt_that_already_fits_is_left_alone() {
        val parts = longParts()

        assertEquals(parts, PromptBudget(contextTokens = 32_000).fit(parts))
    }

    @Test
    fun instructions_and_the_task_survive_the_trim() {
        val parts = longParts()
        val fitted = PromptBudget(contextTokens = 4_096).fit(parts)

        assertEquals(parts.instructions, fitted.instructions)
        assertEquals(parts.task, fitted.task)
    }

    @Test
    fun the_reply_ceiling_follows_the_context_and_stays_sane() {
        assertEquals(1_024, PromptBudget(contextTokens = 32_000).replyCeiling)
        assertEquals(1_024, PromptBudget(contextTokens = 4_096).replyCeiling)
        assertEquals(256, PromptBudget(contextTokens = 1_024).replyCeiling)
    }

    @Test
    fun a_budget_that_still_does_not_fit_drops_the_cheapest_parts_until_it_does() {
        val budget = PromptBudget(contextTokens = 4_096)
        val huge = longParts().copy(
            apps = (1..500).map { "app $it " + "x".repeat(100) },
        )

        val fitted = budget.fit(huge)

        assertTrue(fitted.estimatedTokens() <= budget.inputCeiling)
    }
}

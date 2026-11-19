package com.hopa.agent.prompt

import com.hopa.catalog.testing.knowledge.TestKnowledge
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemPromptTest {
    @Test
    fun the_prompt_carries_every_shared_rule() {
        val prompt = SystemPromptBuilder().build()
        for (rule in TestKnowledge.value.promptRules) {
            for (phrase in rule.phrases) {
                assertTrue(prompt.contains(phrase), "${rule.id} is missing: $phrase")
            }
        }
    }

    @Test
    fun the_web_rule_follows_the_capability() {
        val withWeb = SystemPromptBuilder().build(webAvailable = true)
        val withoutWeb = SystemPromptBuilder().build(webAvailable = false)
        assertTrue(withWeb.contains("web.navigate, web.read, web.click and web.type exist"))
        assertTrue(withoutWeb.contains("There is no web capability"))
    }

    @Test
    fun a_missing_surface_forbids_screen_actions() {
        val prompt = SystemPromptBuilder().build(surfaceAvailable = false)
        assertTrue(prompt.contains("No screen is available"))
    }
}

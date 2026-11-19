package com.hopa.agent

import com.hopa.contracts.ErrorCodes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DecisionParserTest {
    private val parser = DecisionParser()

    @Test
    fun parses_a_contract_action() {
        val outcome = parser.parse(
            """{"schemaVersion":"1","kind":"ui.tap","ref":"e2","timeoutMs":3000}""",
        )
        assertNotNull(outcome.decision)
        assertEquals("ui.tap", outcome.decision?.action?.kind)
        assertEquals("e2", outcome.decision?.action?.ref)
    }

    @Test
    fun rejects_an_unknown_kind() {
        val outcome = parser.parse(
            """{"schemaVersion":"1","kind":"ui.teleport","timeoutMs":3000}""",
        )
        assertNull(outcome.decision)
        assertEquals(ErrorCodes.SCHEMA, outcome.error?.code)
    }

    @Test
    fun rejects_done_with_an_action() {
        val outcome = parser.parse(
            """{"say":"Done.","done":true,"needUser":false,"action":{"kind":"ui.home","timeoutMs":3000}}""",
        )
        assertEquals(ErrorCodes.SCHEMA, outcome.error?.code)
    }

    @Test
    fun rejects_a_question_without_options() {
        val outcome = parser.parse("""{"say":"Which account?","done":false,"needUser":true}""")
        assertEquals(ErrorCodes.SCHEMA, outcome.error?.code)
    }

    @Test
    fun rejects_a_flat_action_that_is_missing_its_target() {
        // The flat shape skips the Decision decoder; it must still pass the
        // same table, or the executor is the first to notice.
        val outcome = parser.parse("""{"kind":"ui.tap"}""")

        assertNull(outcome.decision)
        assertEquals(ErrorCodes.SCHEMA, outcome.error?.code)
        assertEquals("ui.tap needs a ref, or x and y", outcome.error?.message)
    }

    @Test
    fun plain_text_is_text() {
        val outcome = parser.parse("The answer is 4.")
        assertEquals("The answer is 4.", outcome.text)
        assertNull(outcome.decision)
    }

    @Test
    fun accepts_a_fenced_action() {
        val outcome = parser.parse("```json\n{\"kind\":\"ui.home\"}\n```")
        assertEquals("ui.home", outcome.decision?.action?.kind)
    }
}
